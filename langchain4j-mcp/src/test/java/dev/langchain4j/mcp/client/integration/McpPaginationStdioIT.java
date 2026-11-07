package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

class McpPaginationStdioIT extends McpPaginationTestBase {

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
}
