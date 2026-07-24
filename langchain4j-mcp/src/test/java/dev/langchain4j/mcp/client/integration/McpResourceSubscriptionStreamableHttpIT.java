package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.destroyProcessTree;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

class McpResourceSubscriptionStreamableHttpIT extends McpResourceSubscriptionTestBase {

    private static Process process;

    @BeforeAll
    static void setup() throws Exception {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("resource_subscriptions_mcp_server.java");
        McpTransport transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost:8080/mcp")
                .logRequests(true)
                .logResponses(true)
                .build();
        mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .protocolVersion("2026-07-28")
                .onResourceUpdated((mcpClient, uri) -> updatedResourceUris.add(uri))
                .build();
    }

    @AfterAll
    static void teardown() throws Exception {
        if (mcpClient != null) {
            mcpClient.close();
        }
        if (process != null) {
            destroyProcessTree(process);
        }
    }
}
