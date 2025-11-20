package dev.langchain4j.mcp.client.integration;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.websocket.WebSocketMcpTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpHealthWebSocketTransportIT {

    static McpClient mcpClient;
    static Process process;

    @BeforeAll
    static void setup() throws IOException, InterruptedException, TimeoutException {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("logging_mcp_server.java");
        McpTransport transport = WebSocketMcpTransport.builder()
                .url("ws://localhost:8080/mcp/ws")
                .logRequests(true)
                .logResponses(true)
                .build();
        mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .build();
    }

    @AfterAll
    static void teardown() throws Exception {
        if (mcpClient != null) {
            mcpClient.close();
        }
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    @Test
    void health() throws ExecutionException, InterruptedException {
        mcpClient.checkHealth();
        process.destroy();
        process.onExit().get();
        assertThatThrownBy(() -> mcpClient.checkHealth()).rootCause().isInstanceOf(ClosedChannelException.class);
    }
}
