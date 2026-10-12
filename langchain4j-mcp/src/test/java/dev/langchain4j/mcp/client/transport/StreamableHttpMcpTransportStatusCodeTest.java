package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StreamableHttpMcpTransportStatusCodeTest {

    private static final String INITIALIZE_RESULT =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"serverInfo\":{\"name\":\"test-server\",\"version\":\"1.0\"}}}";

    private static final String TOOLS_LIST_RESULT = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}";

    private HttpServer server;
    private StreamableHttpMcpTransport transport;

    private void startServer(int toolsListStatus) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/mcp", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String method = McpJson.parse(body).path("method").asText();
            if ("initialize".equals(method)) {
                exchange.getResponseHeaders().set("Mcp-Session-Id", "session-1");
                respond(exchange, 200, INITIALIZE_RESULT);
            } else if ("notifications/initialized".equals(method)) {
                respond(exchange, 200, "{}");
            } else if (toolsListStatus == 200) {
                respond(exchange, 200, TOOLS_LIST_RESULT);
            } else {
                respond(exchange, toolsListStatus, "{}");
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
    void rejected_credential_carries_the_status_code() throws Exception {
        startServer(401);

        transport.sendInitializeRequest(new McpInitializeRequest(0L)).get(5, TimeUnit.SECONDS);
        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(1L, null));

        assertThat(failureOf(response).statusCode()).isEqualTo(401);
    }

    @Test
    void server_error_carries_its_own_status_code() throws Exception {
        startServer(500);

        transport.sendInitializeRequest(new McpInitializeRequest(0L)).get(5, TimeUnit.SECONDS);
        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(1L, null));

        assertThat(failureOf(response).statusCode()).isEqualTo(500);
    }

    @Test
    void successful_request_is_unaffected() throws Exception {
        startServer(200);

        transport.sendInitializeRequest(new McpInitializeRequest(0L)).get(5, TimeUnit.SECONDS);
        CompletableFuture<String> response = transport.sendRequest(new McpListToolsRequest(1L, null));

        assertThat(response.get(5, TimeUnit.SECONDS)).contains("\"tools\":[]");
    }

    private static HttpException failureOf(CompletableFuture<String> response) {
        Throwable thrown = catchThrowable(() -> response.get(5, TimeUnit.SECONDS));
        assertThat(thrown).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(HttpException.class);
        return (HttpException) thrown.getCause();
    }
}
