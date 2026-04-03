package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.McpToolMetadataKeys.DESTRUCTIVE_HINT;
import static dev.langchain4j.mcp.client.McpToolMetadataKeys.IDEMPOTENT_HINT;
import static dev.langchain4j.mcp.client.McpToolMetadataKeys.OPEN_WORLD_HINT;
import static dev.langchain4j.mcp.client.McpToolMetadataKeys.READ_ONLY_HINT;
import static dev.langchain4j.mcp.client.McpToolMetadataKeys.TITLE_ANNOTATION;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class McpToolMetadataIT {

    static McpClient client;

    @BeforeAll
    static void setup() {
        skipTestsIfJbangNotAvailable();
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(),
                        "--quiet",
                        "--fresh",
                        "run",
                        "-Dquarkus.http.port=8180",
                        getPathToScript("tool_metadata_mcp_server.java")))
                .logEvents(true)
                .build();
        client = new DefaultMcpClient.Builder()
                .transport(transport)
                .build();
    }

    @Test
    public void testToolMetadata() {
        ToolSpecification toolSpec = client.listTools().get(0);
        Map<String, Object> metadata = toolSpec.metadata();
        assertThat(metadata.get(TITLE_ANNOTATION)).isEqualTo("foo");
        assertThat(metadata.get(READ_ONLY_HINT)).isEqualTo(true);
        assertThat(metadata.get(DESTRUCTIVE_HINT)).isEqualTo(true);
        assertThat(metadata.get(IDEMPOTENT_HINT)).isEqualTo(true);
        assertThat(metadata.get(OPEN_WORLD_HINT)).isEqualTo(true);
    }

    @AfterAll
    static void teardown() throws Exception {
        if (client != null) {
            client.close();
        }
    }

}
