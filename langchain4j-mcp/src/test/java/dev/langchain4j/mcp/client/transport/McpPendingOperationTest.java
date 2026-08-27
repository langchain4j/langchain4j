package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpInitializationNotification;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Operations registered through the raw API must be visible to cancellation and to the client's
 * cleanup, which both work off the shared pending-operations map.
 */
class McpPendingOperationTest {

    private static McpOperationHandler handler(Map<Long, CompletableFuture<String>> pending) {
        return new McpOperationHandler(
                pending,
                Collections::emptyList,
                Mockito.mock(McpTransport.class),
                msg -> {},
                () -> {},
                () -> {},
                () -> {},
                uri -> {},
                null,
                () -> {},
                () -> {},
                (id, reason) -> {},
                (id, message) -> {});
    }

    @Test
    void server_cancellation_should_fail_a_raw_operation() {
        Map<Long, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
        McpOperationHandler handler = handler(pending);
        CompletableFuture<String> future = new CompletableFuture<>();
        handler.expectResponse(5L, future);

        handler.onMessage(
                """
                {"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":5,"reason":"stop"}}""");

        assertThat(future).isCompletedExceptionally();
        assertThat(future.handle((v, e) -> e).join()).isInstanceOf(CancellationException.class);
    }

    @Test
    void a_raw_operation_should_be_removable_through_the_shared_map() {
        Map<Long, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
        McpOperationHandler handler = handler(pending);
        handler.expectResponse(7L, new CompletableFuture<>());

        assertThat(pending).containsKey(7L);
        pending.remove(7L);
        assertThat(pending).isEmpty();
    }

    @Test
    void transport_failure_should_fail_a_raw_operation() {
        Map<Long, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
        McpOperationHandler handler = handler(pending);
        CompletableFuture<String> future = new CompletableFuture<>();
        handler.expectResponse(9L, future);

        handler.cancelAllPendingOperations("connection lost");

        assertThat(future).isCompletedExceptionally();
        assertThat(pending).isEmpty();
    }

    @Test
    void the_deprecated_json_api_should_still_receive_its_response() {
        Map<Long, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
        McpOperationHandler handler = handler(pending);
        CompletableFuture<com.fasterxml.jackson.databind.JsonNode> future = new CompletableFuture<>();
        handler.startOperation(11L, future);

        handler.onMessage("""
                {"jsonrpc":"2.0","id":11,"result":{"ok":true}}""");

        assertThat(future.join().get("result").get("ok").asBoolean()).isTrue();
        assertThat(pending).isEmpty();
    }

    @Test
    void the_deprecated_no_reply_method_should_still_receive_messages() {
        // sendMessage is the only bridge with no unit coverage; the client sends ping and
        // roots/list replies through it, and a transport on the old API must still get them.
        java.util.List<McpClientMessage> sent = new java.util.ArrayList<>();
        McpTransport legacy = new McpTransport() {
            public void start(McpOperationHandler h) {}
            @Override
            @SuppressWarnings("removal")
            public void executeOperationWithoutResponse(McpClientMessage request) {
                sent.add(request);
            }
            @Override
            @SuppressWarnings("removal")
            public void executeOperationWithoutResponse(McpCallContext context) {
                sent.add(context.message());
            }
            public void checkHealth() {}
            public void onFailure(Runnable r) {}
            public void close() {}
        };

        legacy.sendMessage(new McpInitializationNotification());

        assertThat(sent).hasSize(1);
    }
}
