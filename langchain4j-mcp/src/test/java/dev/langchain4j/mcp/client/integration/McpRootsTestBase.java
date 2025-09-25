package dev.langchain4j.mcp.client.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpRoot;
import java.util.ArrayList;
import java.util.List;
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
        String result = mcpClient.executeTool(toolExecutionRequest).resultText();
        assertThat(result).isEqualTo("OK");

        // now update the roots
        List<McpRoot> newRoots = new ArrayList<>();
        newRoots.add(new McpRoot("Paul's workspace", "file:///home/paul/workspace"));
        mcpClient.setRoots(newRoots);

        // and verify that the server has asked for the roots again and received them
        toolExecutionRequest = ToolExecutionRequest.builder()
                .name("assertRootsAfterUpdate")
                .arguments("{}")
                .build();
        result = mcpClient.executeTool(toolExecutionRequest).resultText();
        assertThat(result).isEqualTo("OK");
    }
}
