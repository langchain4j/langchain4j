package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.destroyProcessTree;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the modern MCP protocol headers (MCP-Protocol-Version, Mcp-Method, Mcp-Name)
 * are sent on HTTP requests. Uses a server-side header echo tool to read them back.
 */
class McpProtocolHeadersStreamableHttpIT {

    static McpClient mcpClient;
    static Process process;

    @BeforeAll
    static void setup() throws Exception {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("headers_mcp_server.java");
        StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost:8080/mcp")
                .logRequests(true)
                .logResponses(true)
                .build();
        mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .protocolVersion("2026-07-28")
                .build();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (mcpClient != null) {
            mcpClient.close();
        }
        if (process != null) {
            destroyProcessTree(process);
        }
    }

    @Test
    void mcpProtocolVersionHeaderIsSet() {
        String value = echoHeader("MCP-Protocol-Version");
        assertThat(value).isEqualTo("2026-07-28");
    }

    @Test
    void mcpMethodHeaderIsSetForToolsCall() {
        String value = echoHeader("Mcp-Method");
        assertThat(value).isEqualTo("tools/call");
    }

    @Test
    void mcpNameHeaderIsSetForToolsCall() {
        String value = echoHeader("Mcp-Name");
        assertThat(value).isEqualTo("echoHeader");
    }

    private String echoHeader(String headerName) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("echoHeader")
                .arguments("{\"headerName\": \"" + headerName + "\"}")
                .build();
        return mcpClient.executeTool(request).resultText();
    }
}
