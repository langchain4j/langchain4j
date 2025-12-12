package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class McpHeadersStreamableHttpTransportIT extends McpHeadersTestBase {
    private static final Logger log = LoggerFactory.getLogger(McpHeadersStreamableHttpTransportIT.class);


    @BeforeAll
    static void setup() throws IOException, InterruptedException, TimeoutException {
        skipTestsIfJbangNotAvailable();
        process = startProcess();
        StreamableHttpMcpTransport transport = new StreamableHttpMcpTransport.Builder()
                .url("http://localhost:8080/mcp")
                .customHeaders(() -> customHeaders)
                .logRequests(true)
                .logResponses(true)
                .build();
        mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .autoHealthCheckInterval(Duration.ofSeconds(1))
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .reconnectInterval(Duration.ofSeconds(1))
                .build();
        // waiting for mcpClient to be ready
        Thread.sleep(5000);
        destroyProcessTree(process);
        Thread.sleep(5000);
        process = startProcess();
        // waiting for mcpClient to reconnect
        Thread.sleep(5000);
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

    static void destroyProcessTree(Process process) {
        ProcessHandle handle = process.toHandle();
        handle.descendants().forEach(ph -> {
            log.info("Destroying child process: " + ph.pid());
            ph.destroyForcibly();
        });
        process.destroyForcibly();
    }
}
