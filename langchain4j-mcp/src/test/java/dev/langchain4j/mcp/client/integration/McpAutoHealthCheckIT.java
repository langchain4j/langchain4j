package dev.langchain4j.mcp.client.integration;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;


public class McpAutoHealthCheckIT {

    private static Process process;
    private static DefaultMcpClient mcpClient;
    private static McpTransport transport;

    @BeforeAll
    static void setup() throws IOException, InterruptedException, TimeoutException {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("tools_mcp_server.java");
        transport = new HttpMcpTransport.Builder()
                .sseUrl("http://localhost:8080/mcp/sse")
                .logRequests(true)
                .logResponses(true)
                .build();
        mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .reconnectInterval(Duration.ofSeconds(1))
                .autoHealthCheckInterval(Duration.ofMillis(100))
                .build();
    }


    @AfterAll
    static void tearDown() {
        if (mcpClient != null) {
            mcpClient.close();
        }
        if (process != null) {
            process.destroyForcibly();
        }
    }


    @Test
    void shouldStartHealthCheckSchedulerByDefault() throws Exception {
        Field schedulerField = DefaultMcpClient.class.getDeclaredField("healthCheckScheduler");
        schedulerField.setAccessible(true);
        ScheduledExecutorService scheduler = (ScheduledExecutorService) schedulerField.get(mcpClient);
        assertThat(scheduler).isNotNull();
        assertThatCode(() -> TimeUnit.MILLISECONDS.sleep(500))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldDetectDeadServerByDefault() throws Exception {
        executeAToolAndAssertSuccess();
        process.destroy();
        process.onExit().get();
        TimeUnit.MILLISECONDS.sleep(500);
        //Reconnect heartbeat detection
        process = startServerHttp("tools_mcp_server.java");
        TimeUnit.MILLISECONDS.sleep(5_000);
        executeAToolAndAssertSuccess();
    }


    private void executeAToolAndAssertSuccess() {
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": \"abc\"}")
                .build();
        String result = mcpClient.executeTool(toolExecutionRequest);
        assertThat(result).isEqualTo("abc");
    }


}


