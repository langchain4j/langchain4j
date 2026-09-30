package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.destroyProcessTree;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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

    @Test
    void closingClientStopsSubscriptionNotifications() throws Exception {
        List<String> clientAUpdates = new CopyOnWriteArrayList<>();

        // Create a separate client with its own notification list
        McpTransport transportA = StreamableHttpMcpTransport.builder()
                .url("http://localhost:8080/mcp")
                .build();
        McpClient clientA = new DefaultMcpClient.Builder()
                .transport(transportA)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .protocolVersion("2026-07-28")
                .onResourceUpdated((c, uri) -> clientAUpdates.add(uri))
                .build();

        // Subscribe and verify it works
        clientA.subscribeToResources(List.of("file:///status"));
        clientA.executeTool(ToolExecutionRequest.builder()
                .name("updateStatus")
                .arguments("{\"newValue\": \"before-close\"}")
                .build());
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> assertThat(clientAUpdates).contains("file:///status"));

        // Close client A — this should cancel the SSE stream
        clientA.close();

        // Trigger another update via the shared mcpClient
        clientAUpdates.clear();
        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("updateStatus")
                .arguments("{\"newValue\": \"after-close\"}")
                .build());

        // Verify client A receives no more notifications
        Awaitility.await()
                .during(Duration.ofSeconds(3))
                .pollDelay(Duration.ofSeconds(0))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> assertThat(clientAUpdates).isEmpty());
    }
}
