package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpRoot;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled("Roots not working over streamable-http transport yet - opening a global SSE stream is necessary")
class McpRootsStreamableHttpTransportIT extends McpRootsTestBase {

    private static final Logger log = LoggerFactory.getLogger(McpRootsStreamableHttpTransportIT.class);
    private static Process process;

    @BeforeAll
    static void setup() throws IOException, InterruptedException, TimeoutException {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("roots_mcp_server.java");
        List<McpRoot> rootList = new ArrayList<>();
        rootList.add(new McpRoot("David's workspace", "file:///home/david/workspace"));
        McpTransport transport = new StreamableHttpMcpTransport.Builder()
                .url("http://localhost:8080/mcp")
                .logRequests(true)
                .logResponses(true)
                .build();
        mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .roots(rootList)
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
}
