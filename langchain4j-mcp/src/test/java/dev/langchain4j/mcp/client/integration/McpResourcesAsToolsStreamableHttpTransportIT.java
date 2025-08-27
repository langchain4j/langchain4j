package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class McpResourcesAsToolsStreamableHttpTransportIT extends McpResourcesAsToolsTestBase {

    private static Process processAlice;
    private static Process processBob;

    @BeforeAll
    static void setup() throws IOException, InterruptedException, TimeoutException {
        skipTestsIfJbangNotAvailable();
        processAlice = startServerHttp("resources_alice_mcp_server.java", 8180);
        processBob = startServerHttp("resources_bob_mcp_server.java", 8181);
        StreamableHttpMcpTransport transportAlice = new StreamableHttpMcpTransport.Builder()
                .url("http://localhost:8180/mcp")
                .build();
        mcpClientAlice = new DefaultMcpClient.Builder()
                .transport(transportAlice)
                .key("alice")
                .build();
        StreamableHttpMcpTransport transportBob = new StreamableHttpMcpTransport.Builder()
                .url("http://localhost:8181/mcp")
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
