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
 * Verifies that x-mcp-header annotations on tool parameters cause the client
 * to send Mcp-Param-{Name} HTTP headers on tools/call requests.
 * The server reads back the actual HTTP header values to prove they were sent.
 */
class McpParamHeadersStreamableHttpIT {

    static McpClient mcpClient;
    static Process process;

    @BeforeAll
    static void setup() throws Exception {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("param_header_mcp_server.java");
        StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost:8080/mcp")
                .logRequests(true)
                .logResponses(true)
                .build();
        mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(10))
                .protocolVersion("2026-07-28")
                .build();
        // the list of tools has to be already known to be able to tell which HTTP headers to add
        mcpClient.listTools();
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
    void paramHeaderSentForStringParameter() {
        String result = mcpClient
                .executeTool(ToolExecutionRequest.builder()
                        .name("regionEcho")
                        .arguments("{\"region\": \"us-west1\", \"value\": \"hello\"}")
                        .build())
                .resultText();
        assertThat(result).isEqualTo("header=us-west1,body=us-west1");
    }

    @Test
    void paramHeaderWithCustomName() {
        String result = mcpClient
                .executeTool(ToolExecutionRequest.builder()
                        .name("customHeaderName")
                        .arguments("{\"region\": \"eu-central\", \"value\": \"world\"}")
                        .build())
                .resultText();
        assertThat(result).isEqualTo("header=eu-central,body=eu-central");
    }

    @Test
    void paramHeaderWithIntegerAndBoolean() {
        String result = mcpClient
                .executeTool(ToolExecutionRequest.builder()
                        .name("typedHeaders")
                        .arguments("{\"count\": 42, \"verbose\": true, \"value\": \"test\"}")
                        .build())
                .resultText();
        assertThat(result).isEqualTo("countHeader=42,verboseHeader=true");
    }
}
