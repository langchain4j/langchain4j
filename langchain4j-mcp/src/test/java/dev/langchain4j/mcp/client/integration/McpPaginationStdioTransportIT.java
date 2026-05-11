package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class McpPaginationStdioTransportIT {

    static McpClient mcpClient;

    @BeforeAll
    static void setup() {
        skipTestsIfJbangNotAvailable();
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(),
                        "--quiet",
                        "--fresh",
                        "run",
                        "-Dquarkus.mcp.server.tools.page-size=2",
                        "-Dquarkus.mcp.server.resources.page-size=2",
                        "-Dquarkus.mcp.server.prompts.page-size=2",
                        getPathToScript("pagination_mcp_server.java")))
                .logEvents(true)
                .build();
        mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(10))
                .build();
    }

    @AfterAll
    static void teardown() throws Exception {
        if (mcpClient != null) {
            mcpClient.close();
        }
    }

    @Test
    void paginatedListingOfToolsResourcesAndPrompts() {
        // Verify tools - 5 tools with page size 2 means 3 pages
        List<ToolSpecification> tools = mcpClient.listTools();
        assertThat(tools).hasSize(5);
        List<String> toolNames =
                tools.stream().map(ToolSpecification::name).sorted().collect(Collectors.toList());
        assertThat(toolNames).containsExactly("tool1", "tool2", "tool3", "tool4", "tool5");

        // Verify resources - 5 resources with page size 2 means 3 pages
        List<McpResource> resources = mcpClient.listResources();
        assertThat(resources).hasSize(5);
        List<String> resourceNames =
                resources.stream().map(McpResource::name).sorted().collect(Collectors.toList());
        assertThat(resourceNames).containsExactly("resource1", "resource2", "resource3", "resource4", "resource5");

        // Verify prompts - 5 prompts with page size 2 means 3 pages
        List<McpPrompt> prompts = mcpClient.listPrompts();
        assertThat(prompts).hasSize(5);
        List<String> promptNames =
                prompts.stream().map(McpPrompt::name).sorted().collect(Collectors.toList());
        assertThat(promptNames).containsExactly("prompt1", "prompt2", "prompt3", "prompt4", "prompt5");
    }
}
