package dev.langchain4j.mcp.client.integration;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThat;

public class McpHeadersTestBase {

    static Process process;
    static McpClient mcpClient;
    static final Map<String, String> customHeaders = new HashMap<>();

    @AfterAll
    static void clearHeaders() {
        customHeaders.clear();
    }

    static Process startProcess() throws IOException, InterruptedException, TimeoutException {
        return startServerHttp("headers_mcp_server.java");
    }

    @Test
    void reconnect() throws IOException, TimeoutException, InterruptedException {
        customHeaders.put("X-Test-Header", "headerValue1");
        executeEchoHeadersToolAndAssertHeaderValue("headerValue1");

        customHeaders.put("X-Test-Header", "headerValue2");
        executeEchoHeadersToolAndAssertHeaderValue("headerValue2");
    }

    private void executeEchoHeadersToolAndAssertHeaderValue(String expectedValue) {
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("echoHeader")
                .arguments("{\"headerName\": \"X-Test-Header\"}")
                .build();
        String result = mcpClient.executeTool(toolExecutionRequest).resultText();
        assertThat(result).isEqualTo(expectedValue);
    }

}
