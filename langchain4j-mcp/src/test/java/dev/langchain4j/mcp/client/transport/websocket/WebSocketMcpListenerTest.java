package dev.langchain4j.mcp.client.transport.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * A server must not be able to make the client buffer an unbounded amount of data
 * by sending a WebSocket message that never ends.
 */
class WebSocketMcpListenerTest {

    private final McpOperationHandler operationHandler = mock(McpOperationHandler.class);
    private final WebSocket webSocket = mock(WebSocket.class);
    private final AtomicInteger closeCallbacks = new AtomicInteger();

    private WebSocketMcpListener listener(int maxMessageSize) {
        when(webSocket.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(webSocket));
        return new WebSocketMcpListener(
                operationHandler,
                LoggerFactory.getLogger(WebSocketMcpListenerTest.class),
                false,
                closeCallbacks::incrementAndGet,
                () -> {},
                maxMessageSize);
    }

    @Test
    void should_deliver_a_message_split_across_frames_below_the_limit() {
        WebSocketMcpListener listener = listener(10);

        listener.onText(webSocket, "12345", false);
        listener.onText(webSocket, "67890", true);

        verify(operationHandler).handle(argThat(node -> node.toString().equals("1234567890")));
        verify(webSocket, times(2)).request(1);
        verify(webSocket, never()).sendClose(anyInt(), anyString());
    }

    @Test
    void should_close_the_connection_when_a_message_exceeds_the_limit() {
        WebSocketMcpListener listener = listener(10);

        listener.onText(webSocket, "123456", false);
        listener.onText(webSocket, "789012", false);

        verify(operationHandler, never()).handle(any());
        verify(operationHandler).cancelAllPendingOperations(contains("exceeds the maximum size of 10 bytes"));
        assertThat(closeCallbacks.get()).isEqualTo(1);
        verify(webSocket).sendClose(1009, "Message too big");
        verify(webSocket).abort();
        verify(webSocket, times(1)).request(anyLong());
    }

    @Test
    void should_limit_each_message_separately() {
        WebSocketMcpListener listener = listener(10);

        listener.onText(webSocket, "12345678", true);
        listener.onText(webSocket, "87654321", true);

        verify(operationHandler).handle(argThat(node -> node.toString().equals("12345678")));
        verify(operationHandler).handle(argThat(node -> node.toString().equals("87654321")));
        verify(webSocket, never()).sendClose(anyInt(), anyString());
    }

    @Test
    void should_count_the_message_size_in_utf8_bytes() {
        WebSocketMcpListener listener = listener(4);

        listener.onText(webSocket, "žž", true);
        listener.onText(webSocket, "🦋", true);
        verify(webSocket, never()).sendClose(anyInt(), anyString());

        listener.onText(webSocket, "žžž", true);
        verify(webSocket).sendClose(1009, "Message too big");
    }

    @Test
    void should_reject_a_non_positive_limit() {
        assertThatThrownBy(() -> WebSocketMcpTransport.builder()
                        .url("ws://localhost/mcp")
                        .maxMessageSize(0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxMessageSize");
    }
}
