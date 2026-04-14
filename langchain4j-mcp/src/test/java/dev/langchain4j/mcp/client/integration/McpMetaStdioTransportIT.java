package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class McpMetaStdioTransportIT {

    static McpClient mcpClient;

    @BeforeAll
    static void setup() {
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(), "--quiet", "--fresh", "run", getPathToScript("meta_mcp_server.java")))
                .logEvents(true)
                .build();
        mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(10))
                .metaSupplier(ctx -> Map.of(
                        "traceparent", "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01",
                        "custom-key", "custom-value"))
                .build();
    }

    @AfterAll
    static void teardown() throws Exception {
        if (mcpClient != null) {
            mcpClient.close();
        }
    }

    @Test
    void metaFieldsArePassedToServer() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("echoMeta")
                .arguments("{\"key\": \"traceparent\"}")
                .build();
        String result = mcpClient.executeTool(request).resultText();
        assertThat(result).isEqualTo("00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01");
    }

    @Test
    void customMetaFieldIsPassedToServer() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("echoMeta")
                .arguments("{\"key\": \"custom-key\"}")
                .build();
        String result = mcpClient.executeTool(request).resultText();
        assertThat(result).isEqualTo("custom-value");
    }

    @Test
    void missingMetaFieldReturnsNull() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("echoMeta")
                .arguments("{\"key\": \"nonexistent\"}")
                .build();
        String result = mcpClient.executeTool(request).resultText();
        assertThat(result).isEqualTo("null");
    }
}
