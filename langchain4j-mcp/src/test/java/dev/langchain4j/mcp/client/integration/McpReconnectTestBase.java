package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

public abstract class McpReconnectTestBase {

    static Process process;
    static McpClient mcpClient;
    static final Map<String, String> customHeaders = new HashMap<>();

    @AfterAll
    static void clearHeaders() {
        customHeaders.clear();
    }

    static Process startProcess() throws IOException, InterruptedException, TimeoutException {
        return startServerHttp("tools_mcp_server.java");
    }

    @Test
    void reconnect() throws IOException, TimeoutException, InterruptedException {
        executeAToolAndAssertSuccess();

        // kill the server and restart it
        process.destroy();
        process = startProcess();

        // give the MCP client some time to reconnect
        Thread.sleep(5_000);

        executeAToolAndAssertSuccess();
    }

    private void executeAToolAndAssertSuccess() {
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": \"abc\"}")
                .build();
        String result = mcpClient.executeTool(toolExecutionRequest).resultText();
        assertThat(result).isEqualTo("abc");
    }

}
