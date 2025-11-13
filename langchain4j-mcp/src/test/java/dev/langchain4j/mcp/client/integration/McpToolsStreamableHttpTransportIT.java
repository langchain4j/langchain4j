package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class McpToolsStreamableHttpTransportIT extends McpToolsTestBase {

    private static final Logger log = LoggerFactory.getLogger(McpToolsStreamableHttpTransportIT.class);
    private static Process process;

    @BeforeAll
    static void setup() throws IOException, InterruptedException, TimeoutException {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("tools_mcp_server.java");
        McpTransport transport = new StreamableHttpMcpTransport.Builder()
                .url("http://localhost:8080/mcp")
                .logRequests(true)
                .logResponses(true)
                .build();
        mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .build();
    }

    @AfterAll
    static void teardown() throws Exception {
        if (mcpClient != null) {
            mcpClient.close();
        }
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    @Test
    void reinitializesSessionWhenMissingFromServer() throws Exception {
        // Initial call to establish session and verify MCP server is up
        List<ToolSpecification> toolSpecifications = mcpClient.listTools();
        Assertions.assertNotNull(toolSpecifications, "Tool specifications should not be null");
        Assertions.assertDoesNotThrow(() -> mcpClient.checkHealth(), "Health check should pass initially");

        // Simulate server crash
        process.destroyForcibly();

        // Expect failure as server is down
        Assertions.assertThrows(
                Exception.class, () -> mcpClient.checkHealth(), "Expected exception when server is down");

        // Restart the server
        process = startServerHttp("tools_mcp_server.java");

        // Subsequent call should reinitialize session and succeed
        Assertions.assertDoesNotThrow(
                () -> mcpClient.checkHealth(), "Session should be reinitialized and health check should pass");
    }
}
