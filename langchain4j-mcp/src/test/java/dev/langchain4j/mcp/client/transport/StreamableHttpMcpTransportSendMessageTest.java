package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import dev.langchain4j.mcp.protocol.McpPingResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A message that expects no reply - a notification, or a response to a server-initiated request -
 * must not be registered as a pending operation. Its id belongs to the server's id space, so the
 * registration would never be completed, and it could displace a client-initiated request that is
 * still in flight and happens to carry the same id.
 */
class StreamableHttpMcpTransportSendMessageTest {

    private HttpServer server;
    private StreamableHttpMcpTransport transport;
    private final Map<Long, CompletableFuture<String>> pendingOperations = new ConcurrentHashMap<>();
    private final List<String> receivedBodies = new CopyOnWriteArrayList<>();
    private CountDownLatch received;
    private CountDownLatch releaseResponse;

    private void startServer(boolean respondToRequests) throws IOException {
        received = new CountDownLatch(1);
        releaseResponse = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/mcp", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            receivedBodies.add(body);
            received.countDown();
            if (respondToRequests) {
                // hold the response open, so the request stays pending while the test asserts
                try {
                    releaseResponse.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                respond(exchange, 200, "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"tools\":[]}}");
            } else {
                // a response to a server-initiated request: 202, no body
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
            }
        });
        server.start();
        transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost:" + server.getAddress().getPort() + "/mcp")
                .setHttpVersion1_1()
                .build();
        transport.start(new McpOperationHandler(
                pendingOperations,
                Collections::emptyList,
                transport,
                null,
                () -> {},
                null,
                () -> {},
                null,
                null,
                null,
                null,
                null,
                null));
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        if (releaseResponse != null) {
            releaseResponse.countDown();
        }
        if (transport != null) {
            transport.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void a_response_to_a_server_request_is_not_registered_as_pending() throws Exception {
        startServer(false);

        transport.sendMessage(new McpPingResponse(42L));

        assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
        // the id stays on the wire, so that the server can still correlate the response...
        assertThat(receivedBodies.get(0)).contains("\"id\":42");
        // ...but it must not be awaited locally
        assertThat(pendingOperations).isEmpty();
    }

    @Test
    void a_request_is_still_registered_as_pending() throws Exception {
        startServer(true);

        transport.sendRequest(new McpListToolsRequest(7L, null));

        assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(pendingOperations).containsKey(7L);
    }
}
