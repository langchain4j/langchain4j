package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Covers transparent reinitialization of an expired legacy-protocol session on 404: the retry is
 * sent with the new session id, and a second 404 fails fast instead of hanging.
 */
class StreamableHttpMcpTransportSessionExpiryTest {

    private static final String INITIALIZE_RESULT =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"serverInfo\":{\"name\":\"test-server\",\"version\":\"1.0\"}}}";
    private static final String TOOLS_LIST_RESULT = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}";

    private HttpServer server;
    private StreamableHttpMcpTransport transport;
    private final AtomicInteger sessionCounter = new AtomicInteger();
    private final List<String> toolsListSessionIds = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean retrySucceeds;

    private void startServer(boolean retrySucceeds) throws IOException {
        this.retrySucceeds = retrySucceeds;
        sessionCounter.set(0);
        toolsListSessionIds.clear();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/mcp", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String method = McpJson.parse(body).path("method").asText();
            String sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
            if ("initialize".equals(method)) {
                exchange.getResponseHeaders().set("Mcp-Session-Id", "session-" + sessionCounter.incrementAndGet());
                respond(exchange, 200, INITIALIZE_RESULT);
            } else if ("notifications/initialized".equals(method)) {
                respond(exchange, 200, "{}");
            } else if ("tools/list".equals(method)) {
                toolsListSessionIds.add(sessionId);
                if (retrySucceeds && "session-2".equals(sessionId)) {
                    respond(exchange, 200, TOOLS_LIST_RESULT);
                } else {
                    // simulate an expired session
                    respond(
                            exchange,
                            404,
                            "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000,\"message\":\"Session not found\"}}");
                }
            } else {
                respond(exchange, 500, "{}");
            }
        });
        server.start();
        transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost:" + server.getAddress().getPort() + "/mcp")
                .setHttpVersion1_1()
                .build();
        transport.start(new McpOperationHandler(
                new ConcurrentHashMap<>(),
                () -> Collections.emptyList(),
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
        if (transport != null) {
            transport.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldRetryWithTheNewSessionAfterReinitialization() throws Exception {
        startServer(true);

        transport.sendInitializeRequest(new McpInitializeRequest(0L)).get(5, TimeUnit.SECONDS);

        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(1L, null));

        assertThat(response.get(5, TimeUnit.SECONDS)).contains("\"tools\":[]");
        // the first attempt used the original session, the retry used the reinitialized one
        assertThat(toolsListSessionIds).containsExactly("session-1", "session-2");
    }

    @Test
    void shouldFailFastWhenTheRetriedRequestIsRejectedWith404Again() throws Exception {
        startServer(false);

        transport.sendInitializeRequest(new McpInitializeRequest(0L)).get(5, TimeUnit.SECONDS);

        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(1L, null));

        // before the fix this future stayed pending forever; now it must fail fast
        assertThatThrownBy(() -> response.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("after reinitialization");
        assertThat(toolsListSessionIds).containsExactly("session-1", "session-2");
    }
}
