package dev.langchain4j.mcp;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.Optional;

/**
 * @since 1.4.0
 */
public class McpToolExecutor implements ToolExecutor {

    private final McpClient mcpClient;

    // if this name is set, it overrides the name in the execution request - in other words,
    // this executor will always execute the tool with this name
    private final Optional<String> fixedToolName;

    public McpToolExecutor(McpClient mcpClient) {
        this(mcpClient, null);
    }

    public McpToolExecutor(McpClient mcpClient, String fixedToolName) {
        this.mcpClient = ensureNotNull(mcpClient, "mcpClient");
        this.fixedToolName = Optional.ofNullable(fixedToolName);
    }

    @Override
    public String execute(ToolExecutionRequest executionRequest, Object memoryId) {
        return mcpClient.executeTool(sanitizeToolName(executionRequest)).resultText();
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest executionRequest, InvocationContext context) {
        return mcpClient.executeTool(sanitizeToolName(executionRequest));
    }

    private ToolExecutionRequest sanitizeToolName(ToolExecutionRequest executionRequest) {
        if (fixedToolName.isPresent()) {
            return ToolExecutionRequest.builder()
                    .id(executionRequest.id())
                    .name(fixedToolName.get())
                    .arguments(executionRequest.arguments())
                    .build();
        } else {
            return executionRequest;
        }
    }
}
