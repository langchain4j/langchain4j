package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.McpServerMessageContext;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class McpOperationHandlerTest {

    @Test
    void should_complete_pending_operation_exceptionally_on_server_cancelled() throws JsonProcessingException {
        Map<Long, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(42L, future);

        AtomicReference<Long> cancelledId = new AtomicReference<>();
        AtomicReference<String> cancelledReason = new AtomicReference<>();
        McpOperationHandler handler = new McpOperationHandler(
                pending,
                java.util.Collections::emptyList,
                Mockito.mock(McpTransport.class),
                msg -> {},
                () -> {},
                () -> {},
                () -> {},
                uri -> {},
                null,
                () -> {},
                () -> {},
                (id, reason) -> {
                    cancelledId.set(id);
                    cancelledReason.set(reason);
                },
                (id, message) -> {});

        String notification = """
                {
                  "jsonrpc": "2.0",
                  "method": "notifications/cancelled",
                  "params": {
                    "requestId": 42,
                    "reason": "user aborted"
                  }
                }
                """;

        handler.onMessage(notification);

        assertThat(future).isCompletedExceptionally();
        // Inspect the throwable stored on the future via handle() rather than calling
        // future.get(): JDK 25 changed CompletableFuture.reportGet() to throw a fresh
        // CancellationException with a generic message instead of re-throwing the one we
        // supplied. handle() returns the stored throwable as-is on every JDK.
        Throwable stored = future.handle((v, e) -> e).join();
        assertThat(stored)
                .isInstanceOf(CancellationException.class)
                .hasMessageContaining("42")
                .hasMessageContaining("user aborted");
        assertThat(pending).doesNotContainKey(42L);
        assertThat(cancelledId.get()).isEqualTo(42L);
        assertThat(cancelledReason.get()).isEqualTo("user aborted");
    }

    @Test
    void should_invoke_listener_even_when_request_id_is_unknown() throws JsonProcessingException {
        Map<Long, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
        AtomicReference<Long> cancelledId = new AtomicReference<>();
        AtomicReference<String> cancelledReason = new AtomicReference<>();
        McpOperationHandler handler = new McpOperationHandler(
                pending,
                java.util.Collections::emptyList,
                Mockito.mock(McpTransport.class),
                msg -> {},
                () -> {},
                () -> {},
                () -> {},
                uri -> {},
                null,
                () -> {},
                () -> {},
                (id, reason) -> {
                    cancelledId.set(id);
                    cancelledReason.set(reason);
                },
                (id, message) -> {});

        String notification = """
                {
                  "jsonrpc": "2.0",
                  "method": "notifications/cancelled",
                  "params": {
                    "requestId": 7
                  }
                }
                """;

        handler.onMessage(notification);

        assertThat(cancelledId.get()).isEqualTo(7L);
        assertThat(cancelledReason.get()).isNull();
    }

    @Test
    void should_ignore_cancelled_notification_without_request_id() throws JsonProcessingException {
        Map<Long, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(99L, future);

        AtomicReference<Long> cancelledId = new AtomicReference<>();
        McpOperationHandler handler = new McpOperationHandler(
                pending,
                java.util.Collections::emptyList,
                Mockito.mock(McpTransport.class),
                msg -> {},
                () -> {},
                () -> {},
                () -> {},
                uri -> {},
                null,
                () -> {},
                () -> {},
                (id, reason) -> cancelledId.set(id),
                (id, message) -> {});

        String notification = """
                {
                  "jsonrpc": "2.0",
                  "method": "notifications/cancelled",
                  "params": {}
                }
                """;

        handler.onMessage(notification);

        assertThat(future).isNotCompleted();
        assertThat(pending).containsKey(99L);
        assertThat(cancelledId.get()).isNull();
    }

    @Test
    void should_invoke_callback_on_subscription_acknowledged() throws JsonProcessingException {
        AtomicReference<Long> acknowledgedId = new AtomicReference<>();
        AtomicReference<Map<String, Object>> acknowledgedMessage = new AtomicReference<>();
        McpOperationHandler handler = new McpOperationHandler(
                new ConcurrentHashMap<>(),
                java.util.Collections::emptyList,
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
                (id, message) -> {
                    acknowledgedId.set(id);
                    acknowledgedMessage.set(message);
                });

        String notification = """
                {
                  "jsonrpc": "2.0",
                  "method": "notifications/subscriptions/acknowledged",
                  "params": {
                    "_meta": {
                      "io.modelcontextprotocol/subscriptionId": 7
                    },
                    "notifications": {
                      "resourceSubscriptions": ["file:///project/config.json"]
                    }
                  }
                }
                """;

        handler.onMessage(notification);

        assertThat(acknowledgedId.get()).isEqualTo(7L);
        assertThat(acknowledgedMessage.get()).isEqualTo(McpJson.toValue(notification));
    }

    @Test
    void should_ignore_subscription_acknowledged_notification_without_subscription_id() throws JsonProcessingException {
        AtomicReference<Long> acknowledgedId = new AtomicReference<>();
        McpOperationHandler handler = new McpOperationHandler(
                new ConcurrentHashMap<>(),
                java.util.Collections::emptyList,
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
                (id, message) -> acknowledgedId.set(id));

        String notification = """
                {
                  "jsonrpc": "2.0",
                  "method": "notifications/subscriptions/acknowledged",
                  "params": {
                    "notifications": {}
                  }
                }
                """;

        handler.onMessage(notification);

        assertThat(acknowledgedId.get()).isNull();
    }

    @Test
    void reportsNotificationFromServerWithItsMeta() {
        List<McpServerMessageContext> reported = new ArrayList<>();
        McpOperationHandler handler = handlerReportingServerMessages(Mockito.mock(McpTransport.class), reported::add);

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/resources/updated\","
                + "\"params\":{\"uri\":\"file:///a\",\"_meta\":{\"traceparent\":\"00-abc-def-01\"}}}");

        assertThat(reported).hasSize(1);
        McpServerMessageContext context = reported.get(0);
        assertThat(context.method()).isEqualTo("notifications/resources/updated");
        assertThat(context.id()).isNull();
        assertThat(context.params()).containsEntry("uri", "file:///a");
        assertThat(context.meta()).containsEntry("traceparent", "00-abc-def-01");
    }

    @Test
    void reportsRequestFromServerBeforeRespondingToIt() {
        List<String> events = new ArrayList<>();
        McpTransport transport = Mockito.mock(McpTransport.class);
        Mockito.doAnswer(invocation -> events.add("response"))
                .when(transport)
                .sendMessage(Mockito.any(McpClientMessage.class));
        McpOperationHandler handler = handlerReportingServerMessages(
                transport, context -> events.add("reported " + context.method() + " " + context.id()));

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"ping\"}");

        assertThat(events).containsExactly("reported ping 7", "response");
    }

    @Test
    void doesNotReportResponsesToClientRequests() {
        List<McpServerMessageContext> reported = new ArrayList<>();
        Map<Long, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
        pending.put(3L, new CompletableFuture<>());
        McpOperationHandler handler = new McpOperationHandler(
                pending,
                java.util.Collections::emptyList,
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
                (id, message) -> {},
                reported::add);

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{}}");

        assertThat(reported).isEmpty();
        assertThat(pending).isEmpty();
    }

    @Test
    void reportsRequestFromServerWithItsIdAsSent() {
        List<McpServerMessageContext> reported = new ArrayList<>();
        McpOperationHandler handler = handlerReportingServerMessages(Mockito.mock(McpTransport.class), reported::add);

        handler.onMessage("{\"jsonrpc\":\"2.0\",\"id\":\"abc\",\"method\":\"ping\"}");

        assertThat(reported).hasSize(1);
        assertThat(reported.get(0).id()).isEqualTo("abc");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"abc\"", "\"7\"", "7"})
    void respondsToPingWithItsIdAsSent(String id) {
        assertThat(responseTo("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"ping\"}"))
                .isEqualTo(McpJson.toMap("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{}}"));
    }

    @Test
    void respondsToRootsListWithItsIdAsSent() {
        assertThat(responseTo("{\"jsonrpc\":\"2.0\",\"id\":\"abc\",\"method\":\"roots/list\"}"))
                .isEqualTo(McpJson.toMap("{\"jsonrpc\":\"2.0\",\"id\":\"abc\",\"result\":{\"roots\":[]}}"));
    }

    private static Map<String, Object> responseTo(String request) {
        McpTransport transport = Mockito.mock(McpTransport.class);
        McpOperationHandler handler = handlerReportingServerMessages(transport, context -> {});

        handler.onMessage(request);

        ArgumentCaptor<McpClientMessage> response = ArgumentCaptor.forClass(McpClientMessage.class);
        Mockito.verify(transport).sendMessage(response.capture());
        return McpJson.toMap(McpJson.serialize(response.getValue()));
    }

    private static McpOperationHandler handlerReportingServerMessages(
            McpTransport transport, Consumer<McpServerMessageContext> onServerMessage) {
        return new McpOperationHandler(
                new ConcurrentHashMap<>(),
                java.util.Collections::emptyList,
                transport,
                msg -> {},
                () -> {},
                () -> {},
                () -> {},
                uri -> {},
                null,
                () -> {},
                () -> {},
                (id, reason) -> {},
                (id, message) -> {},
                onServerMessage);
    }
}
