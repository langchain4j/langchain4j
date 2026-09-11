package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
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

class McpMetaStdioIT {

    static McpClient mcpClient;

    @BeforeAll
    static void setup() {
        skipTestsIfJbangNotAvailable();
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
                .protocolVersion("2026-07-28")
                .build();
    }

    @AfterAll
    static void teardown() throws Exception {
        if (mcpClient != null) {
            mcpClient.close();
        }
    }

    @Test
    void protocolVersionIsInjectedInMeta() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("echoMeta")
                .arguments("{\"key\": \"io.modelcontextprotocol/protocolVersion\"}")
                .build();
        String result = mcpClient.executeTool(request).resultText();
        assertThat(result).isEqualTo("2026-07-28");
    }

    @Test
    void clientInfoIsInjectedInMeta() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("echoMeta")
                .arguments("{\"key\": \"io.modelcontextprotocol/clientInfo\"}")
                .build();
        String result = mcpClient.executeTool(request).resultText();
        assertThat(result).contains("langchain4j");
    }

    @Test
    void clientCapabilitiesIsInjectedInMeta() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("echoMeta")
                .arguments("{\"key\": \"io.modelcontextprotocol/clientCapabilities\"}")
                .build();
        String result = mcpClient.executeTool(request).resultText();
        assertThat(result).isNotEqualTo("null");
    }

    @Test
    void userSuppliedMetaIsMergedWithProtocolMeta() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("echoMeta")
                .arguments("{\"key\": \"custom-key\"}")
                .build();
        String result = mcpClient.executeTool(request).resultText();
        assertThat(result).isEqualTo("custom-value");
    }

    @Test
    void userSuppliedMetaDoesNotOverwriteProtocolFields() {
        // Even though user supplies meta, protocol fields must still be present
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("echoMeta")
                .arguments("{\"key\": \"io.modelcontextprotocol/protocolVersion\"}")
                .build();
        String result = mcpClient.executeTool(request).resultText();
        assertThat(result).isEqualTo("2026-07-28");
    }
}
