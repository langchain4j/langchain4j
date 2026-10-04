package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.destroyProcessTree;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class McpSubscriptionDeprecationIT {

    private static McpClient mcpClient;
    private static Process process;

    @BeforeAll
    static void beforeAll() throws IOException, InterruptedException, TimeoutException {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("resources_mcp_server.java");
        McpTransport transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost:8080/mcp")
                .build();
        mcpClient = DefaultMcpClient.builder()
                .transport(transport)
                .protocolVersion("2026-07-28")
                .build();
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (mcpClient != null) mcpClient.close();
        if (process != null) destroyProcessTree(process);
    }

    @Test
    void subscribeToResourceThrowsOnModernProtocol() {
        assertThatThrownBy(() -> mcpClient.subscribeToResource("file:///test"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void unsubscribeFromResourceThrowsOnModernProtocol() {
        assertThatThrownBy(() -> mcpClient.unsubscribeFromResource("file:///test"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
