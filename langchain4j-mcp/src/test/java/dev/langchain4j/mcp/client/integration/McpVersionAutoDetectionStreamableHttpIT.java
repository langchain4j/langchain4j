package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.destroyProcessTree;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class McpVersionAutoDetectionStreamableHttpIT {

    private static Process modernProcess;
    private static Process legacyProcess;

    @BeforeAll
    static void beforeAll() throws Exception {
        skipTestsIfJbangNotAvailable();
        modernProcess = startServerHttp("tools_mcp_server.java");
        legacyProcess = startServerHttp("tools_legacy_mcp_server.java", 8081);
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (modernProcess != null) {
            destroyProcessTree(modernProcess);
        }
        if (legacyProcess != null) {
            destroyProcessTree(legacyProcess);
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
    void autoDetectsLegacyProtocolOverStreamableHttp() throws Exception {
        try (McpTransport transport = StreamableHttpMcpTransport.builder()
                        .url("http://localhost:8081/mcp")
                        .build();
                McpClient client =
                        DefaultMcpClient.builder().transport(transport).build()) {
            assertThat(((DefaultMcpClient) client).isModernProtocol()).isFalse();
            assertThat(client.listTools()).isNotEmpty();
        }
    }
}
