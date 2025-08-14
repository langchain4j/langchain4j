package dev.langchain4j.mcp;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutor;

/**
 * @since 1.4.0
 */
public class McpToolExecutor implements ToolExecutor {

    private final McpClient mcpClient;

    public McpToolExecutor(McpClient mcpClient) {
        this.mcpClient = ensureNotNull(mcpClient, "mcpClient");
    }

    @Override
    public String execute(ToolExecutionRequest executionRequest, Object memoryId) {
        return mcpClient.executeTool(executionRequest);
    }
}
