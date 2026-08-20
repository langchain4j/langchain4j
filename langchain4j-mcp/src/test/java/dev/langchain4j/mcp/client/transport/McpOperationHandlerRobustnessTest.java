package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * The dispatch frame is read as plain values rather than a Jackson tree, so the coercions
 * Jackson used to apply have to be reproduced. These cover the cases that differ.
 */
class McpOperationHandlerRobustnessTest {

    private McpOperationHandler handler() {
        return new McpOperationHandler(
                new ConcurrentHashMap<>(),
                List::of,
                Mockito.mock(McpTransport.class),
                m -> {},
                () -> {},
                () -> {},
                () -> {},
                u -> {},
                null,
                null,
                null,
                null);
    }

    @Test
    void completes_an_operation_whose_id_arrives_as_a_json_string() {
        // JSON-RPC allows an id to be a string as well as a number
        McpOperationHandler handler = handler();
        CompletableFuture<String> pending = new CompletableFuture<>();
        handler.startJsonOperation(42L, pending);

        handler.handleJson("{\"id\":\"42\",\"result\":{}}");

        assertThat(pending).isCompleted();
    }

    @Test
    void completes_an_operation_whose_id_arrives_as_a_number() {
        McpOperationHandler handler = handler();
        CompletableFuture<String> pending = new CompletableFuture<>();
        handler.startJsonOperation(7L, pending);

        handler.handleJson("{\"id\":7,\"result\":{}}");

        assertThat(pending).isCompleted();
    }

    @Test
    void ignores_a_batch_array_rather_than_failing() {
        assertThatCode(() -> handler().handleJson("[{\"id\":1,\"result\":{}}]"))
                .doesNotThrowAnyException();
    }

    @Test
    void ignores_a_malformed_payload_rather_than_failing() {
        assertThatCode(() -> handler().handleJson("not json")).doesNotThrowAnyException();
    }

    @Test
    void ignores_an_unusable_id_rather_than_failing() {
        assertThatCode(() -> handler().handleJson("{\"id\":{},\"result\":{}}"))
                .doesNotThrowAnyException();
    }

    @Test
    void a_bare_json_null_should_be_ignored_rather_than_throw() {
        // it parses without error but is not a message; throwing here kills the stdio reader thread
        assertThatCode(() -> handler().handleJson("null")).doesNotThrowAnyException();
        assertThatCode(() -> handler().handleJson("")).doesNotThrowAnyException();
    }
}
