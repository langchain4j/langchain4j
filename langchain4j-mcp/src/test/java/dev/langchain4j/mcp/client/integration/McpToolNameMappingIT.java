package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * This test verifies that the McpToolProvider can be configured with a tool name mapper
 * so that all tools retrieved from the provider will have their name adjusted by the mapper.
 */
public class McpToolNameMappingIT {

    static McpClient mcpClient;

    static McpToolProvider toolProvider;

    @BeforeAll
    static void setup() throws IOException, InterruptedException, TimeoutException {
        skipTestsIfJbangNotAvailable();
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(), "--quiet", "--fresh", "run", getPathToScript("tools_mcp_server.java")))
                .logEvents(true)
                .build();
        mcpClient = new DefaultMcpClient.Builder().transport(transport).build();
        toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                .toolNameMapper((client, toolSpec) -> {
                    // Prefix all tool names with "myprefix_"
                    return "myprefix_" + toolSpec.name();
                })
                .build();
    }

    @Test
    public void testMapping() {
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);
        assertThat(toolProviderResult.tools().keySet()).hasSizeGreaterThan(0);
        assertThat(toolProviderResult.tools().keySet().stream()
                        .allMatch(spec -> spec.name().startsWith("myprefix_")))
                .isTrue();

        ToolExecutor echoStringExecutor = toolProviderResult.toolExecutorByName("myprefix_echoString");
        assertThat(echoStringExecutor).isNotNull();
        String result = echoStringExecutor.execute(
                ToolExecutionRequest.builder()
                        // note that we don't need to call set the "name" here because this executor was created
                        // specifically for the tool logically named "myprefix_echoString", physically named
                        // "echoString"
                        .arguments("{\"input\": \"hello\"}")
                        .build(),
                null);
        assertThat(result).isEqualTo("hello");
    }

    @AfterAll
    static void teardown() throws Exception {
        if (mcpClient != null) {
            mcpClient.close();
        }
    }
}
