package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class McpVersionAutoDetectionStdioIT {

    @BeforeAll
    static void beforeAll() {
        skipTestsIfJbangNotAvailable();
    }

    @Test
    void autoDetectsModernProtocolOverStdio() throws Exception {
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(), "--quiet", "--fresh", "run", getPathToScript("tools_mcp_server.java")))
                .logEvents(true)
                .build();
        try (McpClient client = DefaultMcpClient.builder().transport(transport).build()) {
            assertThat(((DefaultMcpClient) client).isModernProtocol()).isTrue();
            assertThat(client.listTools()).isNotEmpty();
        }
    }

    @Test
    void autoDetectsLegacyProtocolOverStdio() throws Exception {
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(),
                        "--quiet",
                        "--fresh",
                        "run",
                        getPathToScript("tools_legacy_mcp_server.java")))
                .logEvents(true)
                .build();
        try (McpClient client = DefaultMcpClient.builder().transport(transport).build()) {
            assertThat(((DefaultMcpClient) client).isModernProtocol()).isFalse();
            assertThat(client.listTools()).isNotEmpty();
        }
    }
}
