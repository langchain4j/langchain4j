package dev.langchain4j.mcp;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutor;

class McpToolExecutor implements ToolExecutor {
    private final McpClient mcpClient;

    public McpToolExecutor(McpClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String execute(ToolExecutionRequest executionRequest, Object memoryId) {
        return mcpClient.executeTool(executionRequest);
    }
}
