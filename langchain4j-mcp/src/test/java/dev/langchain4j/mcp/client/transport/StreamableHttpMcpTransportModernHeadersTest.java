package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpGetPromptRequest;
import dev.langchain4j.mcp.protocol.McpPingRequest;
import dev.langchain4j.mcp.protocol.McpReadResourceRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * In the modern protocol, the transport sends the JSON-RPC method as {@code Mcp-Method} and, for
 * {@code prompts/get}, {@code tools/call} and {@code resources/read}, the name or URI as {@code Mcp-Name}.
 */
class StreamableHttpMcpTransportModernHeadersTest {

    private HttpServer server;
    private StreamableHttpMcpTransport transport;
    private final Map<String, String> headers = new ConcurrentHashMap<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/mcp", exchange -> {
            exchange.getRequestBody().readAllBytes();
            headers.clear();
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
            respond(exchange, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"resultType\":\"complete\"}}");
        });
        server.start();
        transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost:" + server.getAddress().getPort() + "/mcp")
                .setHttpVersion1_1()
                .build();
        transport.start(new McpOperationHandler(
                new ConcurrentHashMap<>(),
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
        transport.setModernProtocol(true);
        transport.setProtocolVersion("2026-07-28");
    }

    @AfterEach
    void stopServer() throws IOException {
        transport.close();
        server.stop(0);
    }

    @Test
    void prompts_get_sends_the_prompt_name() throws Exception {
        send(new McpGetPromptRequest(1L, "summarize", Map.of()));

        assertThat(headers).containsEntry("mcp-method", "prompts/get").containsEntry("mcp-name", "summarize");
    }

    @Test
    void resources_read_sends_the_uri() throws Exception {
        send(new McpReadResourceRequest(1L, "file:///tmp/test.txt"));

        assertThat(headers)
                .containsEntry("mcp-method", "resources/read")
                .containsEntry("mcp-name", "file:///tmp/test.txt");
    }

    @Test
    void a_request_without_params_sends_only_the_method() throws Exception {
        send(new McpPingRequest(1L));

        assertThat(headers).containsEntry("mcp-method", "ping").doesNotContainKey("mcp-name");
    }

    private void send(McpClientMessage message) throws Exception {
        transport.sendRequest(message).get(5, TimeUnit.SECONDS);
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
