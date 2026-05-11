package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies subscribing to and unsubscribing from resource list changes (on a single resource denoted by an URI).
 *
 * TODO: also add an error handling test after https://github.com/quarkiverse/quarkus-mcp-server/issues/716 is resolved in quarkus-mcp-server
 */
class McpResourceSubscriptionStdioTransportIT {

    static McpClient mcpClient;
    static List<String> updatedResourceUris = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void setup() {
        skipTestsIfJbangNotAvailable();
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(),
                        "--quiet",
                        "--fresh",
                        "run",
                        getPathToScript("resource_subscriptions_mcp_server.java")))
                .logEvents(true)
                .build();
        mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .onResourceUpdated((mcpClient, uri) -> updatedResourceUris.add(uri))
                .build();
    }

    @AfterAll
    static void teardown() throws Exception {
        if (mcpClient != null) {
            mcpClient.close();
        }
    }

    @Test
    public void subscribeAndReceiveResourceUpdate() {
        updatedResourceUris.clear();

        // subscribe to the status resource
        mcpClient.subscribeToResource("file:///status");

        // verify initial value
        McpReadResourceResult initialResult = mcpClient.readResource("file:///status");
        assertThat(((McpTextResourceContents) initialResult.contents().get(0)).text())
                .isEqualTo("initial");

        // update the resource on the server
        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("updateStatus")
                .arguments("{\"newValue\": \"updated\"}")
                .build());

        // wait a bit for the notification to arrive
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollDelay(Duration.ofSeconds(0))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> assertThat(updatedResourceUris).contains("file:///status"));

        // re-read the resource to verify the content changed
        McpReadResourceResult updatedResult = mcpClient.readResource("file:///status");
        assertThat(((McpTextResourceContents) updatedResult.contents().get(0)).text())
                .isEqualTo("updated");

        // unsubscribe
        mcpClient.unsubscribeFromResource("file:///status");

        // clear the list and update again
        updatedResourceUris.clear();
        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("updateStatus")
                .arguments("{\"newValue\": \"updated-again\"}")
                .build());

        // wait and verify no notification was received after unsubscribing
        Awaitility.await()
                .during(Duration.ofSeconds(4))
                .pollDelay(Duration.ofSeconds(0))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    assertThat(updatedResourceUris).doesNotContain("file:///status");
                });
    }
}
