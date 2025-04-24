package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class McpReconnectIT {

    private static Process process;
    private static DefaultMcpClient mcpClient;

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
                .reconnectInterval(Duration.ofSeconds(1))
                .build();
    }

    @Test
    public void testReconnect() throws IOException, TimeoutException, InterruptedException {
        executeAToolAndAssertSuccess();

        // kill the server and restart it
        process.destroy();
        process = startServerHttp("tools_mcp_server.java");

        // give the MCP client some time to reconnect
        Thread.sleep(5_000);

        executeAToolAndAssertSuccess();
    }

    @AfterAll
    static void tearDown() throws IOException, InterruptedException {
        if (mcpClient != null) {
            mcpClient.close();
        }
        if (process != null) {
            process.destroyForcibly();
        }
    }

    private void executeAToolAndAssertSuccess() {
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": \"abc\"}")
                .build();
        String result = mcpClient.executeTool(toolExecutionRequest);
        assertThat(result).isEqualTo("abc");
    }
}
