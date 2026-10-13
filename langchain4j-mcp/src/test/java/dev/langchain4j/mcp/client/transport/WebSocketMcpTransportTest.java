package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.mcp.client.transport.websocket.WebSocketMcpTransport;
import dev.langchain4j.mcp.protocol.McpPingRequest;
import java.lang.reflect.Field;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;

class WebSocketMcpTransportTest {

    @Test
    void shouldApplyCustomSslContext() throws Exception {
        SSLContext customContext = SSLContext.getInstance("TLS");
        customContext.init(null, null, null);

        WebSocketMcpTransport transport = WebSocketMcpTransport.builder()
                .url("ws://localhost/mcp")
                .sslContext(customContext)
                .build();

        assertThat(extractSslContext(transport)).isSameAs(customContext);
    }

    @Test
    void shouldReloadSslContext() throws Exception {
        SSLContext initialContext = SSLContext.getInstance("TLS");
        initialContext.init(null, null, null);
        SSLContext reloadedContext = SSLContext.getInstance("TLS");
        reloadedContext.init(null, null, null);

        WebSocketMcpTransport transport = WebSocketMcpTransport.builder()
                .url("ws://localhost/mcp")
                .sslContext(initialContext)
                .build();

        transport.reloadSslContext(reloadedContext);

        assertThat(extractSslContext(transport)).isSameAs(reloadedContext);
    }

    @Test
    void shouldPropagateSendFailure() throws Exception {
        RuntimeException sendFailure = new RuntimeException("send failed");
        WebSocketMcpTransport transport = transportSending(CompletableFuture.failedFuture(sendFailure));

        CompletableFuture<String> result = transport.sendRequest(new McpPingRequest(42L));

        assertThat(result).isCompletedExceptionally();
        assertThatThrownBy(result::get).isInstanceOf(ExecutionException.class).hasCause(sendFailure);
    }

    @Test
    void shouldLeaveRequestPendingWhenSendSucceeds() throws Exception {
        WebSocketMcpTransport transport = transportSending(CompletableFuture.completedFuture(mock(WebSocket.class)));

        CompletableFuture<String> result = transport.sendRequest(new McpPingRequest(42L));

        assertThat(result).isNotDone();
    }

    private static WebSocketMcpTransport transportSending(CompletableFuture<WebSocket> sendResult) throws Exception {
        WebSocket webSocket = mock(WebSocket.class);
        when(webSocket.sendText(anyString(), eq(true))).thenReturn(sendResult);
        WebSocketMcpTransport transport =
                WebSocketMcpTransport.builder().url("ws://localhost/mcp").build();
        setField(transport, "operationHandler", mock(McpOperationHandler.class));
        webSocketRef(transport).set(CompletableFuture.completedFuture(webSocket));
        return transport;
    }

    private static SSLContext extractSslContext(WebSocketMcpTransport transport) throws Exception {
        Field field = WebSocketMcpTransport.class.getDeclaredField("sslContext");
        field.setAccessible(true);
        return (SSLContext) field.get(transport);
    }

    @SuppressWarnings("unchecked")
    private static AtomicReference<CompletableFuture<WebSocket>> webSocketRef(WebSocketMcpTransport transport)
            throws Exception {
        Field field = WebSocketMcpTransport.class.getDeclaredField("webSocketRef");
        field.setAccessible(true);
        return (AtomicReference<CompletableFuture<WebSocket>>) field.get(transport);
    }

    private static void setField(WebSocketMcpTransport transport, String name, Object value) throws Exception {
        Field field = WebSocketMcpTransport.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(transport, value);
    }
}
