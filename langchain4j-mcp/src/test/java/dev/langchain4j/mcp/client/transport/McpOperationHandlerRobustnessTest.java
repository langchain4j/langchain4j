package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.langchain4j.exception.JsonException;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * The dispatch frame is read as plain values rather than a Jackson tree, so the coercions
 * Jackson used to apply have to be reproduced. These cover the cases that differ.
 */
class McpOperationHandlerRobustnessTest {

    private final McpTransport transport = Mockito.mock(McpTransport.class);

    private McpOperationHandler handler() {
        return new McpOperationHandler(
                new ConcurrentHashMap<>(),
                List::of,
                transport,
                m -> {},
                () -> {},
                () -> {},
                () -> {},
                u -> {},
                null,
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
        handler.expectResponse(42L, pending);

        handler.onMessage("{\"id\":\"42\",\"result\":{}}");

        assertThat(pending).isCompleted();
    }

    @Test
    void completes_an_operation_whose_id_arrives_as_a_number() {
        McpOperationHandler handler = handler();
        CompletableFuture<String> pending = new CompletableFuture<>();
        handler.expectResponse(7L, pending);

        handler.onMessage("{\"id\":7,\"result\":{}}");

        assertThat(pending).isCompleted();
    }

    @Test
    void ignores_a_batch_array_rather_than_failing() {
        assertThatCode(() -> handler().onMessage("[{\"id\":1,\"result\":{}}]"))
                .doesNotThrowAnyException();
    }

    @Test
    void a_malformed_payload_should_throw_so_the_transport_can_decide() {
        // the caller decides: a response body that cannot be read fails the pending operation,
        // while a stream reader logs it and carries on. Swallowing it here left the streamable-HTTP
        // and Docker transports with a future nobody completed.
        // Either of the two types the transports catch: the Jackson 2 codecs report the
        // IllegalArgumentException this has always thrown, langchain4j-jackson3 a JsonException.
        assertThatThrownBy(() -> handler().onMessage("not json"))
                .isInstanceOfAny(JsonException.class, IllegalArgumentException.class);
    }

    @Test
    void ignores_an_unusable_id_rather_than_failing() {
        assertThatCode(() -> handler().onMessage("{\"id\":{},\"result\":{}}"))
                .doesNotThrowAnyException();
    }

    @Test
    void valid_json_that_is_not_a_jsonrpc_object_should_be_ignored() {
        // the tree model ignored these rather than failing, so they stay ignored
        assertThatCode(() -> handler().onMessage("null")).doesNotThrowAnyException();
        assertThatCode(() -> handler().onMessage("42")).doesNotThrowAnyException();
        assertThatCode(() -> handler().onMessage("\"a string\"")).doesNotThrowAnyException();
    }

    @Test
    void an_empty_body_should_throw_like_any_other_unreadable_input() {
        assertThatThrownBy(() -> handler().onMessage(""))
                .isInstanceOfAny(JsonException.class, IllegalArgumentException.class);
    }

    @Test
    void answers_a_server_ping_whose_id_is_a_non_numeric_string() {
        // the MCP Java SDK sends server-initiated requests with ids such as "<session id>-0"
        handler().onMessage("{\"jsonrpc\":\"2.0\",\"id\":\"3f1c9a2e-0\",\"method\":\"ping\"}");

        assertThat(idOfTheSentResponse()).isEqualTo("3f1c9a2e-0");
    }

    @Test
    void answers_a_server_roots_list_whose_id_is_a_non_numeric_string() {
        handler().onMessage("{\"jsonrpc\":\"2.0\",\"id\":\"3f1c9a2e-1\",\"method\":\"roots/list\"}");

        assertThat(idOfTheSentResponse()).isEqualTo("3f1c9a2e-1");
    }

    @Test
    void answers_a_server_request_whose_id_is_a_numeric_string_with_the_same_string() {
        handler().onMessage("{\"jsonrpc\":\"2.0\",\"id\":\"7\",\"method\":\"ping\"}");

        assertThat(idOfTheSentResponse()).isEqualTo("7");
    }

    @Test
    void answers_a_server_request_whose_id_is_a_number_with_the_same_number() {
        handler().onMessage("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"ping\"}");

        assertThat(idOfTheSentResponse()).isInstanceOf(Number.class).hasToString("7");
    }

    @Test
    void does_not_answer_a_server_request_with_an_unusable_id() {
        handler().onMessage("{\"jsonrpc\":\"2.0\",\"id\":{},\"method\":\"ping\"}");

        verify(transport, never()).sendMessage(Mockito.any(McpClientMessage.class));
    }

    @SuppressWarnings("unchecked")
    private Object idOfTheSentResponse() {
        ArgumentCaptor<McpClientMessage> response = ArgumentCaptor.forClass(McpClientMessage.class);
        verify(transport).sendMessage(response.capture());
        return ((Map<String, Object>) McpJson.toValue(McpJson.serialize(response.getValue()))).get("id");
    }
}
