package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpLoggingHttpTransportIT extends McpLoggingTestBase {

    private static final Logger log = LoggerFactory.getLogger(McpLoggingHttpTransportIT.class);
    private static Process process;

    @BeforeAll
    public static void setup() throws IOException, InterruptedException, TimeoutException {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("logging_mcp_server.java");
        logMessageHandler = new TestMcpLogHandler();
        McpTransport transport = new HttpMcpTransport.Builder()
                .sseUrl("http://localhost:8080/mcp/sse")
                .logRequests(true)
                .logResponses(true)
                .build();
        mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .logHandler(logMessageHandler)
                .build();
    }

    @AfterAll
    public static void teardown() throws Exception {
        if (mcpClient != null) {
            mcpClient.close();
        }
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }
}
