package dev.langchain4j.jackson3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpException;
import dev.langchain4j.mcp.client.transport.McpJson;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.registryclient.McpRegistryJson;
import dev.langchain4j.mcp.registryclient.model.McpServer;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end check of the MCP client: {@link DefaultMcpClient} over {@link StreamableHttpMcpTransport},
 * talking to a local server, with Jackson 2 absent from the classpath.
 */
class McpWithoutJackson2Test {

    private HttpServer server;
    private final Map<String, String> toolCallHeaders = new ConcurrentHashMap<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/mcp", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> request = McpJson.toMap(body);
            Object id = request.get("id");
            String method = (String) request.get("method");
            if (id == null) {
                respond(exchange, 202, null);
                return;
            }
            if ("tools/call".equals(method)) {
                toolCallHeaders.put("Mcp-Method", String.valueOf(exchange.getRequestHeaders().getFirst("Mcp-Method")));
                toolCallHeaders.put("Mcp-Name", String.valueOf(exchange.getRequestHeaders().getFirst("Mcp-Name")));
            }
            if ("prompts/list".equals(method)) {
                respond(exchange, 200, "{\"jsonrpc\":\"2.0\",\"id\":" + id
                        + ",\"error\":{\"code\":-32001,\"message\":\"Rate limited\",\"data\":{\"retryAfter\":5}}}");
                return;
            }
            respond(exchange, 200, "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":" + resultOf(method) + "}");
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void lists_and_calls_tools_with_the_legacy_protocol() throws Exception {
        try (McpClient client = client("2025-11-25")) {
            assertListsAndCallsTools(client);
        }
    }

    @Test
    void lists_and_calls_tools_with_the_modern_protocol() throws Exception {
        try (McpClient client = client("2026-07-28")) {
            assertListsAndCallsTools(client);
        }

        assertThat(toolCallHeaders).containsEntry("Mcp-Method", "tools/call").containsEntry("Mcp-Name", "echo");
    }

    @Test
    void reads_the_data_of_an_error_response() throws Exception {
        try (McpClient client = client("2025-11-25")) {
            Throwable thrown = catchThrowable(client::listPrompts);

            assertThat(thrown).isInstanceOf(McpException.class);
            McpException exception = (McpException) thrown;
            assertThat(exception.errorCode()).isEqualTo(-32001);
            assertThat(exception.errorDataAsMap()).containsEntry("retryAfter", 5);
        }
    }

    @Test
    void reads_registry_metadata() {
        String json =
                """
                {"name":"io.example/server","version":"1.0.0",
                 "_meta":{"io.modelcontextprotocol.registry/publisher-provided":{"tool":"ci-publisher"}}}
                """;

        McpServer server = McpRegistryJson.fromJson(json, McpServer.class);

        assertThat(server.getMeta().publisherProvided()).containsEntry("tool", "ci-publisher");
    }

    private static void assertListsAndCallsTools(McpClient client) {
        List<ToolSpecification> tools = client.listTools();
        assertThat(tools).extracting(ToolSpecification::name).containsExactly("echo");

        ToolExecutionResult result = client.executeTool(ToolExecutionRequest.builder()
                .name("echo")
                .arguments("{\"text\":\"hi\"}")
                .build());
        assertThat(result.resultText()).isEqualTo("echo: hi");
    }

    private McpClient client(String protocolVersion) {
        StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost:" + server.getAddress().getPort() + "/mcp")
                .setHttpVersion1_1()
                .build();
        return DefaultMcpClient.builder()
                .transport(transport)
                .protocolVersion(protocolVersion)
                .subscribeToToolListChanges(false)
                .subscribeToPromptListChanges(false)
                .subscribeToResourceListChanges(false)
                .build();
    }

    private static String resultOf(String method) {
        return switch (method) {
            case "server/discover" ->
                """
                {"supportedVersions":["2026-07-28"],"capabilities":{"tools":{}},
                 "serverInfo":{"name":"test-server","version":"1.0"},"resultType":"complete"}""";
            case "initialize" ->
                """
                {"protocolVersion":"2025-11-25","capabilities":{"tools":{}},
                 "serverInfo":{"name":"test-server","version":"1.0"}}""";
            case "tools/list" ->
                """
                {"tools":[{"name":"echo","description":"Echoes the text back",
                 "inputSchema":{"type":"object","properties":{"text":{"type":"string"}}}}],
                 "resultType":"complete"}""";
            case "tools/call" ->
                """
                {"content":[{"type":"text","text":"echo: hi"}],"resultType":"complete"}""";
            default -> "{\"resultType\":\"complete\"}";
        };
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(statusCode, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
