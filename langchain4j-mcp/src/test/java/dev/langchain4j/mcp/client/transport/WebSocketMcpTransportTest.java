package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.mcp.client.transport.websocket.WebSocketMcpTransport;
import dev.langchain4j.mcp.protocol.McpPingRequest;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    @Test
    void stopShouldAbortTheConnectionAndAllowANewOne() throws Exception {
        WebSocket webSocket = mock(WebSocket.class);
        WebSocketMcpTransport transport =
                WebSocketMcpTransport.builder().url("ws://localhost/mcp").build();
        webSocketRef(transport).set(CompletableFuture.completedFuture(webSocket));

        transport.stop();

        // the connection is aborted and forgotten, so that the next start opens a new one, while the transport
        // itself, unlike after close(), is still usable
        verify(webSocket).abort();
        assertThat(webSocketRef(transport).get()).isNull();
        assertThat(isClosed(transport)).isFalse();

        transport.close();
        assertThat(isClosed(transport)).isTrue();
    }

    @Test
    void stopShouldAbortAConnectionThatIsStillBeingEstablished() throws Exception {
        WebSocket webSocket = mock(WebSocket.class);
        WebSocketMcpTransport transport =
                WebSocketMcpTransport.builder().url("ws://localhost/mcp").build();
        CompletableFuture<WebSocket> connection = new CompletableFuture<>();
        webSocketRef(transport).set(connection);

        transport.stop();
        connection.complete(webSocket);

        verify(webSocket).abort();
    }

    @Test
    void stopShouldEndTheConnectionEvenWhenTheServerNeverAnswers() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            WebSocketMcpTransport transport = transportConnectingTo(server);
            try (Socket connection = connect(transport, server)) {

                transport.stop();

                // reaches the end of the stream instead of timing out: stop() does not depend on the server
                connection.setSoTimeout(10_000);
                connection.getInputStream().readAllBytes();
            }
            transport.close();
        }
    }

    @Test
    void lateCloseOfTheOldConnectionShouldNotAffectTheNewOne() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            WebSocketMcpTransport transport = transportConnectingTo(server);
            try (Socket oldConnection = connect(transport, server)) {
                transport.stop();
                try (Socket newConnection = connect(transport, server)) {
                    CompletableFuture<WebSocket> current = webSocketRef(transport).get();

                    sendCloseFrame(oldConnection);
                    Thread.sleep(500); // the client handles the frame asynchronously

                    assertThat(webSocketRef(transport).get()).isSameAs(current);
                }
            }
            transport.close();
        }
    }

    private static WebSocketMcpTransport transportConnectingTo(ServerSocket server) {
        return WebSocketMcpTransport.builder()
                .url("ws://localhost:" + server.getLocalPort() + "/mcp")
                .build();
    }

    private static Socket connect(WebSocketMcpTransport transport, ServerSocket server) throws Exception {
        CompletableFuture<Socket> accepted = CompletableFuture.supplyAsync(() -> acceptHandshake(server));
        transport.start(mock(McpOperationHandler.class));
        Socket connection = accepted.get(10, TimeUnit.SECONDS);
        webSocketRef(transport).get().get(10, TimeUnit.SECONDS);
        return connection;
    }

    private static Socket acceptHandshake(ServerSocket server) {
        try {
            Socket socket = server.accept();
            StringBuilder request = new StringBuilder();
            while (!request.toString().endsWith("\r\n\r\n")) {
                request.append((char) socket.getInputStream().read());
            }
            Matcher key = Pattern.compile("(?i)Sec-WebSocket-Key: (.*)\r\n").matcher(request);
            assertThat(key.find()).isTrue();
            byte[] accept = MessageDigest.getInstance("SHA-1")
                    .digest((key.group(1).trim() + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                            .getBytes(StandardCharsets.ISO_8859_1));
            String response = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + Base64.getEncoder().encodeToString(accept) + "\r\n\r\n";
            socket.getOutputStream().write(response.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            return socket;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void sendCloseFrame(Socket connection) {
        try {
            connection.getOutputStream().write(new byte[] {(byte) 0x88, 0x02, 0x03, (byte) 0xE8});
            connection.getOutputStream().flush();
        } catch (IOException e) {
            // the client has already dropped this connection, so there is nobody to receive the frame
        }
    }

    private static boolean isClosed(WebSocketMcpTransport transport) throws Exception {
        Field field = WebSocketMcpTransport.class.getDeclaredField("closed");
        field.setAccessible(true);
        return field.getBoolean(transport);
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
