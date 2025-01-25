package dev.langchain4j.mcp.client.integration;

import static org.assertj.core.api.Assertions.fail;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpHttpTransportIT extends McpTransportTestBase {

    private static final Logger log = LoggerFactory.getLogger(McpHttpTransportIT.class);
    private static Process process;

    @BeforeAll
    public static void setup() throws IOException, InterruptedException, TimeoutException, URISyntaxException {
        skipTestsIfJbangNotAvailable();
        String path = getPathToScript("tools_mcp_server.java");
        String[] command = new String[] {getJBangCommand(), "--quiet", "--fresh", "run", path};
        log.info("Starting the MCP server using command: " + Arrays.toString(command));
        process = new ProcessBuilder().command(command).inheritIO().start();
        waitForPort(8080, 120);
        log.info("MCP server has started");
        McpTransport transport = new HttpMcpTransport.Builder()
                .sseUrl("http://localhost:8080/mcp/sse")
                .logRequests(true)
                .logResponses(true)
                .build();
        mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .build();
    }

    private static void waitForPort(int port, int timeoutSeconds) throws InterruptedException, TimeoutException {
        Request request = new Request.Builder().url("http://localhost:" + port).build();
        long start = System.currentTimeMillis();
        OkHttpClient client = new OkHttpClient();
        while (System.currentTimeMillis() - start < timeoutSeconds * 1000) {
            try {
                client.newCall(request).execute();
                return;
            } catch (IOException e) {
                TimeUnit.SECONDS.sleep(1);
            }
        }
        throw new TimeoutException("Port " + port + " did not open within " + timeoutSeconds + " seconds");
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

    /**
     * Verify that the MCP client fails gracefully when the server returns a 404.
     */
    @Test
    public void wrongUrl() throws Exception {
        McpClient badClient = null;
        try {
            McpTransport transport = new HttpMcpTransport.Builder()
                    .sseUrl("http://localhost:8080/WRONG")
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            badClient = new DefaultMcpClient.Builder()
                    .transport(transport)
                    .toolExecutionTimeout(Duration.ofSeconds(4))
                    .build();
            fail("Expected an exception");
        } catch (Exception e) {
            // ok
        } finally {
            if (badClient != null) {
                badClient.close();
            }
        }
    }
}
