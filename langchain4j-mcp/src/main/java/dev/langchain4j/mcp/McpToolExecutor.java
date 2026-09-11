package dev.langchain4j.mcp;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * @since 1.4.0
 */
public class McpToolExecutor implements ToolExecutor {

    private final McpClient mcpClient;

    // if this name is set, it overrides the name in the execution request - in other words,
    // this executor will always execute the tool with this name
    private final Optional<String> fixedToolName;

    private final boolean returnToolResultAttributes;

    public McpToolExecutor(McpClient mcpClient) {
        this(mcpClient, null);
    }

    public McpToolExecutor(McpClient mcpClient, String fixedToolName) {
        this(mcpClient, fixedToolName, false);
    }

    McpToolExecutor(McpClient mcpClient, String fixedToolName, boolean returnToolResultAttributes) {
        this.mcpClient = ensureNotNull(mcpClient, "mcpClient");
        this.fixedToolName = Optional.ofNullable(fixedToolName);
        this.returnToolResultAttributes = returnToolResultAttributes;
    }

    @Override
    public String execute(ToolExecutionRequest executionRequest, Object memoryId) {
        InvocationContext invocationContext =
                InvocationContext.builder().chatMemoryId(memoryId).build();
        return mcpClient
                .executeTool(sanitizeToolName(executionRequest), invocationContext)
                .resultText();
    }

    @Override
    public ToolExecutionResult executeWithContext(
            ToolExecutionRequest executionRequest, InvocationContext invocationContext) {
        ToolExecutionResult result = mcpClient.executeTool(sanitizeToolName(executionRequest), invocationContext);
        if (returnToolResultAttributes || result.attributes().isEmpty()) {
            return result;
        }
        return result.toBuilder().attributes(Map.of()).build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Non-blocking: delegates to
     * {@link McpClient#executeToolAsync(ToolExecutionRequest, InvocationContext)}, so no thread is held
     * while the tool executes on the MCP server.
     */
    @Override
    public CompletableFuture<ToolExecutionResult> executeAsync(
            ToolExecutionRequest executionRequest, InvocationContext invocationContext) {
        return mcpClient.executeToolAsync(sanitizeToolName(executionRequest), invocationContext);
    }

    private ToolExecutionRequest sanitizeToolName(ToolExecutionRequest executionRequest) {
        if (fixedToolName.isPresent()) {
            return executionRequest.toBuilder().name(fixedToolName.get()).build();
        } else {
            return executionRequest;
        }
    }
}
