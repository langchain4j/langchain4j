package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class McpHealthStdioTransportIT {

    static McpClient mcpClient;
    static McpTransport transport;
    static Process process;

    @BeforeAll
    static void setup() {
        StdioMcpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(), "--quiet", "--fresh", "run", getPathToScript("logging_mcp_server.java")))
                .logEvents(true)
                .build();
        McpHealthStdioTransportIT.transport = transport;
        mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .build();
        process = transport.getProcess();
    }

    @AfterAll
    static void teardown() throws Exception {
        if (mcpClient != null) {
            mcpClient.close();
        }
    }

    @Test
    public void testHealth() throws ExecutionException, InterruptedException {
        mcpClient.checkHealth();
        process.destroy();
        process.onExit().get();
        assertThatThrownBy(() -> mcpClient.checkHealth())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Process is not alive");
    }
}
