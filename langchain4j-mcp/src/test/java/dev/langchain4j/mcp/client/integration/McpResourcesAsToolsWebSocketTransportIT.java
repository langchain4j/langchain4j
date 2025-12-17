package dev.langchain4j.mcp.client.integration;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.websocket.WebSocketMcpTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class McpResourcesAsToolsWebSocketTransportIT extends McpResourcesAsToolsTestBase {

    private static Process processAlice;
    private static Process processBob;

    @BeforeAll
    static void setup() throws IOException, InterruptedException, TimeoutException {
        skipTestsIfJbangNotAvailable();
        processAlice = startServerHttp("resources_alice_mcp_server.java", 8180);
        processBob = startServerHttp("resources_bob_mcp_server.java", 8181);
        WebSocketMcpTransport transportAlice = WebSocketMcpTransport.builder()
                .url("ws://localhost:8180/mcp/ws")
                .build();
        mcpClientAlice = new DefaultMcpClient.Builder()
                .transport(transportAlice)
                .key("alice")
                .build();
        WebSocketMcpTransport transportBob = WebSocketMcpTransport.builder()
                .url("ws://localhost:8181/mcp/ws")
                .build();
        mcpClientBob = new DefaultMcpClient.Builder()
                .transport(transportBob)
                .key("bob")
                .build();
    }

    @AfterAll
    static void teardown() throws Exception {
        if (mcpClientAlice != null) {
            mcpClientAlice.close();
        }
        if (mcpClientBob != null) {
            mcpClientBob.close();
        }
        if (processAlice != null && processAlice.isAlive()) {
            processAlice.destroyForcibly();
        }
        if (processBob != null && processBob.isAlive()) {
            processBob.destroyForcibly();
        }
    }
}
