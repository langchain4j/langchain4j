package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class McpVersionAutoDetectionStdioIT {

    /**
     * The first jbang server of a CI run has to resolve and download its dependencies before it
     * answers anything, which has taken close to a minute. Detection treats a server that stays
     * silent as a legacy server, so the timeout is raised well past that here: these tests are
     * about which protocol gets chosen, not about how quickly a server boots.
     */
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);

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
        try (McpClient client = DefaultMcpClient.builder()
                .transport(transport)
                .initializationTimeout(STARTUP_TIMEOUT)
                .build()) {
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
        // without the raised timeout this assertion would also hold for a modern server that was
        // merely too slow to answer, so the test would pass for the wrong reason
        try (McpClient client = DefaultMcpClient.builder()
                .transport(transport)
                .initializationTimeout(STARTUP_TIMEOUT)
                .build()) {
            assertThat(((DefaultMcpClient) client).isModernProtocol()).isFalse();
            assertThat(client.listTools()).isNotEmpty();
        }
    }
}
