package dev.langchain4j.mcp.client.integration;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MultipleMcpToolsIT {

    static McpClient mcpBaseClient;
    static McpClient mcpNumericClient;

    private static Process process1;
    private static Process process2;

    private static final Logger log = LoggerFactory.getLogger(MultipleMcpToolsIT.class);

    @BeforeAll
    static void setup() throws IOException, InterruptedException, TimeoutException {
        skipTestsIfJbangNotAvailable();
        process1 = startServerHttp("tools_mcp_server.java", 8080);
        mcpBaseClient = buildMcpClient("base-mcp", 8080);

        process2 = startServerHttp("tools_numeric_mcp_server.java", 8081);
        mcpNumericClient = buildMcpClient("numeric-mcp", 8081);
    }

    private static McpClient buildMcpClient(String key, int port) {
        McpTransport transport = new HttpMcpTransport.Builder()
                .sseUrl("http://localhost:" + port + "/mcp/sse")
                .logRequests(true)
                .logResponses(true)
                .build();
        return new DefaultMcpClient.Builder()
                .key(key)
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .build();
    }

    @AfterAll
    static void teardown() throws Exception {
        if (mcpBaseClient != null) {
            mcpBaseClient.close();
        }
        if (process1 != null && process1.isAlive()) {
            process1.destroyForcibly();
        }
        if (mcpNumericClient != null) {
            mcpNumericClient.close();
        }
        if (process2 != null && process2.isAlive()) {
            process2.destroyForcibly();
        }
    }

    @Test
    public void duplicatedTool() {
        assertThatThrownBy(() -> McpToolProvider.builder().mcpClients(mcpBaseClient, mcpNumericClient).build().provideTools(null))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("echoInteger");
    }

    @Test
    public void filterTools() {
        ToolProviderResult toolProviderResult = McpToolProvider.builder()
                .mcpClients(mcpBaseClient, mcpNumericClient)
                .filter((mcpClient, tool) -> !tool.name().startsWith("echo"))
                .build()
                .provideTools(null);

        Set<ToolSpecification> tools = toolProviderResult.tools().keySet();
        assertThat(tools).hasSize(3);
        assertThat(tools).extracting(ToolSpecification::name).containsExactlyInAnyOrder("longOperation", "error", "errorResponse");
    }

    @Test
    public void filterToolsByName() {
        ToolProviderResult toolProviderResult = McpToolProvider.builder()
                .mcpClients(mcpBaseClient, mcpNumericClient)
                .filterToolNames("echoString", "echoLong")
                .build()
                .provideTools(null);

        Set<ToolSpecification> tools = toolProviderResult.tools().keySet();
        assertThat(tools).hasSize(2);
        assertThat(tools).extracting(ToolSpecification::name).containsExactlyInAnyOrder("echoString", "echoLong");
    }

    @Test
    public void useMultipleFilters() {
        ToolProviderResult toolProviderResult = McpToolProvider.builder()
                .mcpClients(mcpBaseClient, mcpNumericClient)
                .filterToolNames("echoInteger")
                .filter((mcpClient, tool) -> mcpClient.key().equals("base-mcp"))
                .build()
                .provideTools(null);

        Set<ToolSpecification> tools = toolProviderResult.tools().keySet();
        assertThat(tools).hasSize(1);
        assertThat(tools).extracting(ToolSpecification::name).containsExactlyInAnyOrder("echoInteger");

        ToolExecutor executor = toolProviderResult.toolExecutorByName("echoInteger");
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("echoInteger")
                .arguments("{\"input\": 2}")
                .build();
        String toolExecutionResultString = executor.execute(toolExecutionRequest, null);
        assertThat(toolExecutionResultString).isEqualTo("2");
    }

    @Test
    public void filterDuplicatedTools() {
        // Filter out the base-mcp version of echoInteger
        ToolProviderResult toolProviderResult = McpToolProvider.builder()
                .mcpClients(mcpBaseClient, mcpNumericClient)
                .filter((mcpClient, tool) ->
                        !tool.name().startsWith("echoInteger") || mcpClient.key().equals("numeric-mcp"))
                .build()
                .provideTools(null);
        assertThat(toolProviderResult.tools()).hasSize(9);

        // Execute the numeric-mcp version of echoInteger which adds 1 to the input
        ToolExecutor executor = toolProviderResult.toolExecutorByName("echoInteger");
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("echoInteger")
                .arguments("{\"input\": 2}")
                .build();
        String toolExecutionResultString = executor.execute(toolExecutionRequest, null);
        assertThat(toolExecutionResultString).isEqualTo("3");
    }
}
