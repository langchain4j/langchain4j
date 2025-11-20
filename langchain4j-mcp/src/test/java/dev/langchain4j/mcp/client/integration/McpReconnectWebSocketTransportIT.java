package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.websocket.WebSocketMcpTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

class McpReconnectWebSocketTransportIT extends McpReconnectTestBase {

    @BeforeAll
    static void setup() throws IOException, InterruptedException, TimeoutException {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("tools_mcp_server.java");
        McpTransport transport = WebSocketMcpTransport.builder()
                .url("ws://localhost:8080/mcp/ws")
                .logRequests(true)
                .logResponses(true)
                .build();
        mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .reconnectInterval(Duration.ofSeconds(1))
                .build();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (mcpClient != null) {
            mcpClient.close();
        }
        if (process != null) {
            process.destroyForcibly();
        }
    }


}
