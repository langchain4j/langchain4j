package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.fail;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class McpToolsHttpTransportIT extends McpToolsTestBase {

    private static final Logger log = LoggerFactory.getLogger(McpToolsHttpTransportIT.class);
    private static Process process;

    @BeforeAll
    static void setup() throws IOException, InterruptedException, TimeoutException {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("tools_mcp_server.java");
        McpTransport transport = new HttpMcpTransport.Builder()
                .sseUrl("http://localhost:8080/mcp/sse")
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

    /**
     * Verify that the MCP client fails gracefully when the server returns a 404.
     */
    @Test
    void wrongUrl() throws Exception {
        McpClient badClient = null;
        try {
            McpTransport transport = new HttpMcpTransport.Builder()
                    .sseUrl("http://localhost:8080/WRONG")
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            badClient = new DefaultMcpClient.Builder()
                    .transport(transport)
                    .toolExecutionTimeout(Duration.ofSeconds(4))
                    .build();
            fail("Expected an exception");
        } catch (Exception e) {
            // ok
        } finally {
            if (badClient != null) {
                badClient.close();
            }
        }
    }
}
