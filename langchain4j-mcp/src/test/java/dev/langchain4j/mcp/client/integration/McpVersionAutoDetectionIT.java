package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.destroyProcessTree;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class McpVersionAutoDetectionIT {

    private static Process process;

    @BeforeAll
    static void beforeAll() throws Exception {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("tools_mcp_server.java");
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (process != null) {
            destroyProcessTree(process);
        }
    }

    @Test
    void autoDetectsModernProtocolOverStreamableHttp() throws Exception {
        try (McpTransport transport = StreamableHttpMcpTransport.builder()
                        .url("http://localhost:8080/mcp")
                        .build();
                McpClient client =
                        DefaultMcpClient.builder().transport(transport).build()) {
            assertThat(((DefaultMcpClient) client).isModernProtocol()).isTrue();
            assertThat(client.listTools()).isNotEmpty();
        }
    }

    @Test
    void autoDetectsModernProtocolOverStdio() throws Exception {
        try (McpTransport transport = new StdioMcpTransport.Builder()
                        .command(List.of(
                                getJBangCommand(),
                                "--quiet",
                                "--fresh",
                                "run",
                                getPathToScript("tools_mcp_server.java")))
                        .logEvents(true)
                        .build();
                McpClient client =
                        DefaultMcpClient.builder().transport(transport).build()) {
            assertThat(((DefaultMcpClient) client).isModernProtocol()).isTrue();
            assertThat(client.listTools()).isNotEmpty();
        }
    }

    @Test
    void autoDetectsLegacyProtocolOverStdio() throws Exception {
        try (McpTransport transport = new StdioMcpTransport.Builder()
                        .command(List.of(
                                getJBangCommand(),
                                "--quiet",
                                "--fresh",
                                "run",
                                getPathToScript("tools_legacy_mcp_server.java")))
                        .logEvents(true)
                        .build();
                McpClient client =
                        DefaultMcpClient.builder().transport(transport).build()) {
            assertThat(((DefaultMcpClient) client).isModernProtocol()).isFalse();
            assertThat(client.listTools()).isNotEmpty();
        }
    }
}
