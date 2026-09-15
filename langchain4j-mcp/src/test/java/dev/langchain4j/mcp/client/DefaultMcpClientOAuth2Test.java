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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the whole client (not just the transport) against an MCP server protected by an OAuth 2.0
 * authorization server: the first call obtains a token with the client-credentials grant, and after
 * the server revokes that token the next call is transparently retried with a fresh one.
 */
class DefaultMcpClientOAuth2Test {

    private static final String TOOLS =
            "[{\"name\":\"echo\",\"description\":\"Echo\",\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}]";

    /** The number of the token the authorization server issues next, and the only one the MCP server accepts. */
    private final AtomicInteger currentToken = new AtomicInteger(1);

    private final List<String> tokenRequests = new CopyOnWriteArrayList<>();
    private final List<String> mcpRequests = new CopyOnWriteArrayList<>();

    private HttpServer server;
    private McpClient client;

    @BeforeEach
    void startServers() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

        server.createContext("/token", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            tokenRequests.add(body);
            respond(
                    exchange,
                    200,
                    "{\"access_token\":\"tok-" + currentToken.get()
                            + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        });

        server.createContext("/mcp", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var json = McpJson.parse(body);
            String method = json.path("method").asText();
            String id = json.path("id").asText();
            List<String> authorization = exchange.getRequestHeaders().get("Authorization");
            mcpRequests.add(method + " " + authorization);
            if (authorization == null || !authorization.equals(List.of("Bearer tok-" + currentToken.get()))) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer error=\"invalid_token\"");
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
                            "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"tools\":" + TOOLS + "}}");
                case "ping" -> respond(exchange, 200, "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{}}");
                default ->
                    respond(
                            exchange,
                            200,
                            "{\"jsonrpc\":\"2.0\",\"id\":" + id
                                    + ",\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}");
            }
        });
        server.start();

        String base = "http://localhost:" + server.getAddress().getPort();
        OAuth2ClientCredentialsAuthProvider auth = OAuth2ClientCredentialsAuthProvider.builder()
                .tokenEndpoint(base + "/token")
                .clientId("agent")
                .clientSecret("secret")
                .scopes("mcp:tools")
                .resource(base + "/mcp")
                .build();
        client = new DefaultMcpClient.Builder()
                .transport(StreamableHttpMcpTransport.builder()
                        .url(base + "/mcp")
                        .setHttpVersion1_1()
                        .authProvider(auth)
                        .build())
                .protocolVersion("2025-11-25")
                .autoHealthCheck(false)
                .build();
    }

    @AfterEach
    void stopServers() throws Exception {
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
    void client_obtains_a_token_and_recovers_from_revocation() {
        List<ToolSpecification> tools = client.listTools();

        assertThat(tools).extracting(ToolSpecification::name).containsExactly("echo");
        assertThat(tokenRequests).hasSize(1);
        assertThat(tokenRequests.get(0)).contains("grant_type=client_credentials", "scope=mcp%3Atools", "resource=");
        assertThat(mcpRequests)
                .containsExactly(
                        "initialize [Bearer tok-1]",
                        "notifications/initialized [Bearer tok-1]",
                        "tools/list [Bearer tok-1]");

        // the authorization server rotates: tok-1 is now rejected, tok-2 is what it issues next
        currentToken.set(2);
        mcpRequests.clear();

        client.checkHealth();

        assertThat(tokenRequests).hasSize(2);
        assertThat(mcpRequests).containsExactly("ping [Bearer tok-1]", "ping [Bearer tok-2]");
    }
}
