package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for behavior related to closing of MCP clients.
 */
class McpClientClosingIT {

    @BeforeAll
    static void setup() {
        skipTestsIfJbangNotAvailable();
    }

    /**
     * Check that the subprocess is stopped after calling McpClient.close().
     */
    @Test
    void stdioClosesProcess() throws InterruptedException {
        StdioMcpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(), "--quiet", "--fresh", "run", getPathToScript("tools_mcp_server.java")))
                .build();
        DefaultMcpClient mcpClient =
                new DefaultMcpClient.Builder().transport(transport).build();
        Process process = transport.getProcess();
        mcpClient.listTools();
        mcpClient.close();
        process.waitFor(60, TimeUnit.SECONDS);
        assertThat(process.isAlive()).isFalse();
    }

    /**
     * Check that a closed MCP client throws an IllegalStateException whenever an operation is attempted.
     */
    @Test
    void closedClientThrowsIllegalStateException() {
        StdioMcpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(), "--quiet", "--fresh", "run", getPathToScript("tools_mcp_server.java")))
                .build();
        DefaultMcpClient mcpClient;
        mcpClient = new DefaultMcpClient.Builder().transport(transport).build();
        mcpClient.close();
        assertThatThrownBy(mcpClient::listTools).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> mcpClient.executeTool(
                        ToolExecutionRequest.builder().id("1").arguments("{}}").build()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(mcpClient::listResources).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> mcpClient.readResource("resource://foo")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(mcpClient::listPrompts).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> mcpClient.getPrompt("prompt", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(mcpClient::listResourceTemplates).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(mcpClient::checkHealth).isInstanceOf(IllegalStateException.class);
    }
}
