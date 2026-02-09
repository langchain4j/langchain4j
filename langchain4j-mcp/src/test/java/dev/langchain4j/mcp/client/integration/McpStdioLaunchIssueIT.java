package dev.langchain4j.mcp.client.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTimeout;

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
class McpStdioLaunchIssueIT {

    /**
     * With a non-existent command, the client will fail immediately after calling the ProcessBuilder.
     */
    @Test
    void withNonExistentCommand() throws Exception {
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
     * With a command that does exist but exits soon after it is executed,
     * verify that the client doesn't wait until the initializationTimeout
     * and fails immediately.
     */
    @Test
    void failingJBangScript() throws Exception {
        McpServerHelper.skipTestsIfJbangNotAvailable();
        StdioMcpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of(McpServerHelper.getJBangCommand(), "nonexistent"))
                .build();
        assertThatThrownBy(() -> {
                    assertTimeout(Duration.ofSeconds(20), () -> {
                        McpClient client = null;
                        try {
                            client = new DefaultMcpClient.Builder()
                                    .initializationTimeout(Duration.ofSeconds(30))
                                    .transport(transport)
                                    .build();
                        } finally {
                            if (client != null) {
                                client.close();
                            }
                        }
                    });
                })
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Process has exited");
    }
}
