package dev.langchain4j.mcp.client.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class McpRootsTestBase {

    static McpClient mcpClient;

    private static final Logger log = LoggerFactory.getLogger(McpRootsTestBase.class);

    @Test
    public void verifyServerHasReceivedTools() {
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("assertRoots")
                .arguments("{}")
                .build();
        String result = mcpClient.executeTool(toolExecutionRequest);
        assertThat(result).isEqualTo("OK");
    }
}
