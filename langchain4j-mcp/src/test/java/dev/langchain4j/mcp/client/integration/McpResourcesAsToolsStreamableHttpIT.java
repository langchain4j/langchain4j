package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.destroyProcessTree;
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
class McpResourcesAsToolsStreamableHttpIT extends McpResourcesAsToolsTestBase {

    private static Process processAlice;
    private static Process processBob;

    @BeforeAll
    static void setup() throws IOException, InterruptedException, TimeoutException {
        skipTestsIfJbangNotAvailable();
        processAlice = startServerHttp("resources_alice_mcp_server.java", 8182);
        processBob = startServerHttp("resources_bob_mcp_server.java", 8183);
        StreamableHttpMcpTransport transportAlice = new StreamableHttpMcpTransport.Builder()
                .url("http://localhost:8182/mcp")
                .build();
        mcpClientAlice = new DefaultMcpClient.Builder()
                .transport(transportAlice)
                .key("alice")
                .protocolVersion("2026-07-28")
                .build();
        StreamableHttpMcpTransport transportBob = new StreamableHttpMcpTransport.Builder()
                .url("http://localhost:8183/mcp")
                .build();
        mcpClientBob = new DefaultMcpClient.Builder()
                .transport(transportBob)
                .key("bob")
                .protocolVersion("2026-07-28")
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
            destroyProcessTree(processAlice);
        }
        if (processBob != null && processBob.isAlive()) {
            destroyProcessTree(processBob);
        }
    }
}
