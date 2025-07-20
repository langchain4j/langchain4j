package dev.langchain4j.mcp.client.integration;

import static org.junit.jupiter.api.Assertions.fail;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verify that the MCP client with stdio transport doesn't get stuck when launching the subprocess fails.
 */
public class McpStdioLaunchIssueIT {

    /**
     * With a non-existent command, the client will fail immediately after calling the ProcessBuilder.
     */
    @Test
    void testWithNonExistentCommand() throws Exception {
        McpClient client = null;
        try {
            StdioMcpTransport transport = new StdioMcpTransport.Builder()
                    .command(Collections.singletonList("WRONG-COMMAND"))
                    .build();
            client = new DefaultMcpClient.Builder().transport(transport).build();
            fail("The MCP client should have failed by now");
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            // OK
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    /**
     * With a command that does exist but fails after it is executed,
     * the client will fail after the "initialization timeout".
     */
    @Test
    void testFailingJBangScript() throws Exception {
        McpServerHelper.skipTestsIfJbangNotAvailable();
        McpClient client = null;
        try {
            StdioMcpTransport transport = new StdioMcpTransport.Builder()
                    .command(List.of(McpServerHelper.getJBangCommand(), "nonexistent"))
                    .build();
            client = new DefaultMcpClient.Builder()
                    .initializationTimeout(Duration.ofSeconds(1)) // to make the test pass faster
                    .transport(transport)
                    .build();
            fail("The MCP client should have failed by now");
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            // OK
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }
}
