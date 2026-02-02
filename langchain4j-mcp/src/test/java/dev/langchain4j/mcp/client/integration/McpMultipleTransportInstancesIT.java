package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration test that validates multiple StdioMcpTransport instances can coexist
 * without ExecutorService conflicts. This test specifically addresses the issue
 * that was causing CI failures in PR #4486 and led to revert #4522.
 *
 * The root cause was that multiple transport instances sharing the same
 * DefaultExecutorProvider.getDefaultExecutorService() singleton would fail
 * when the first instance shutdown the shared executor during close().
 */
class McpMultipleTransportInstancesIT {

    @BeforeAll
    static void setup() {
        skipTestsIfJbangNotAvailable();
    }

    @Test
    void shouldHandleMultipleTransportInstancesWithoutExecutorConflicts() throws Exception {
        // Create first transport instance
        StdioMcpTransport transport1 = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(), "--quiet", "--fresh", "run", getPathToScript("resources_mcp_server.java")))
                .build();

        McpClient client1 = new DefaultMcpClient.Builder()
                .transport(transport1)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .build();

        // Initialize and use first client
        List<McpResource> result1 = client1.listResources();
        assertNotNull(result1);
        assertEquals(2, result1.size());

        // Create second transport instance (this would fail with RejectedExecutionException
        // if the shared executor was improperly shutdown by the first instance)
        StdioMcpTransport transport2 = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(), "--quiet", "--fresh", "run", getPathToScript("resources_mcp_server.java")))
                .build();

        McpClient client2 = new DefaultMcpClient.Builder()
                .transport(transport2)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .build();

        // Initialize and use second client - this validates the executor is still functional
        List<McpResource> result2 = client2.listResources();
        assertNotNull(result2);
        assertEquals(2, result2.size());

        // Close both clients - should not cause executor conflicts
        client1.close();
        client2.close();
    }
}
