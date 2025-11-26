package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
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
 * This test verifies that the McpToolProvider can be configured with a tool specification mapper
 * so that all tools retrieved from the provider will have their specification adjusted by the mapper.
 */
public class McpToolSpecificationMappingIT {

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
                .toolSpecificationMapper((client, toolSpec) -> {
                    // Prefix all tool names with "myprefix_" and convert the description to uppercase
                    return toolSpec.toBuilder()
                            .name("myprefix_" + toolSpec.name())
                            .description(toolSpec.description().toUpperCase())
                            .build();
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

        assertThat(toolProviderResult
                        .toolSpecificationByName("myprefix_echoString")
                        .description())
                .isEqualTo("ECHOES A STRING");
        ToolExecutor echoStringExecutor = toolProviderResult.toolExecutorByName("myprefix_echoString");
        assertThat(echoStringExecutor).isNotNull();
        String result = echoStringExecutor.execute(
                ToolExecutionRequest.builder()
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
