package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.auth.OAuth2ClientCredentialsAuthProvider;
import dev.langchain4j.mcp.client.transport.McpJson;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The full flow of the MCP authorization specification for a client acting on its own behalf,
 * with nothing configured but the client credentials: unauthenticated request, 401 with
 * {@code resource_metadata}, protected resource metadata, authorization server metadata, token,
 * retried request.
 */
class DefaultMcpClientOAuth2DiscoveryTest {

    private final List<String> requests = new CopyOnWriteArrayList<>();

    private HttpServer server;
    private McpClient client;
    private String base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        base = "http://localhost:" + server.getAddress().getPort();

        server.createContext("/.well-known/oauth-protected-resource/mcp", exchange -> {
            requests.add("GET prm");
            respond(
                    exchange,
                    200,
                    "{\"resource\":\"" + base + "/mcp\",\"authorization_servers\":[\"" + base
                            + "/realms/mcp\"],\"scopes_supported\":[\"mcp:tools\"]}");
        });
        server.createContext("/realms/mcp/.well-known/openid-configuration", exchange -> {
            requests.add("GET openid-configuration");
            respond(
                    exchange,
                    200,
                    "{\"issuer\":\"" + base + "/realms/mcp\",\"token_endpoint\":\"" + base
                            + "/realms/mcp/token\",\"grant_types_supported\":[\"client_credentials\"],"
                            + "\"token_endpoint_auth_methods_supported\":[\"client_secret_basic\"]}");
        });
        server.createContext("/realms/mcp/token", exchange -> {
            String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add("POST token " + form);
            respond(exchange, 200, "{\"access_token\":\"tok-1\",\"token_type\":\"Bearer\",\"expires_in\":300}");
        });
        server.createContext("/mcp", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var json = McpJson.parse(body);
            String method = json.path("method").asText();
            String id = json.path("id").asText();
            List<String> authorization = exchange.getRequestHeaders().get("Authorization");
            requests.add("POST " + method + " " + authorization);
            if (authorization == null || !authorization.equals(List.of("Bearer tok-1"))) {
                exchange.getResponseHeaders()
                        .set(
                                "WWW-Authenticate",
                                "Bearer resource_metadata=\"" + base + "/.well-known/oauth-protected-resource/mcp\","
                                        + " scope=\"mcp:tools\"");
                respond(exchange, 401, "");
                return;
            }
            switch (method) {
                case "initialize" ->
                    respond(
                            exchange,
                            200,
                            "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"protocolVersion\":\"2025-11-25\","
                                    + "\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"test\",\"version\":\"1\"}}}");
                case "notifications/initialized" -> respond(exchange, 200, "");
                case "tools/list" ->
                    respond(
                            exchange,
                            200,
                            "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"tools\":[{\"name\":\"echo\","
                                    + "\"description\":\"Echo\",\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}]}}");
                default ->
                    respond(
                            exchange,
                            200,
                            "{\"jsonrpc\":\"2.0\",\"id\":" + id
                                    + ",\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}");
            }
        });
        server.start();

        client = new DefaultMcpClient.Builder()
                .transport(StreamableHttpMcpTransport.builder()
                        .url(base + "/mcp")
                        .setHttpVersion1_1()
                        .authProvider(OAuth2ClientCredentialsAuthProvider.builder()
                                .clientId("agent")
                                .clientSecret("secret")
                                .build())
                        .build())
                .protocolVersion("2025-11-25")
                .autoHealthCheck(false)
                .build();
    }

    @AfterEach
    void stop() throws Exception {
        if (client != null) {
            client.close();
        }
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) {
            exchange.sendResponseHeaders(statusCode, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    void client_discovers_the_authorization_server_and_lists_tools() {
        List<ToolSpecification> tools = client.listTools();

        assertThat(tools).extracting(ToolSpecification::name).containsExactly("echo");
        assertThat(requests).hasSize(7);
        assertThat(requests.get(0)).isEqualTo("POST initialize null");
        assertThat(requests.get(1)).isEqualTo("GET prm");
        assertThat(requests.get(2)).isEqualTo("GET openid-configuration");
        assertThat(requests.get(3))
                .startsWith("POST token ")
                .contains("grant_type=client_credentials")
                .contains("scope=mcp%3Atools")
                .contains("resource=" + java.net.URLEncoder.encode(base + "/mcp", StandardCharsets.UTF_8));
        assertThat(requests.subList(4, 7))
                .containsExactly(
                        "POST initialize [Bearer tok-1]",
                        "POST notifications/initialized [Bearer tok-1]",
                        "POST tools/list [Bearer tok-1]");
    }
}
