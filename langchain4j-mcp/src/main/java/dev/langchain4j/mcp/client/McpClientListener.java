package dev.langchain4j.mcp.client;

import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.Map;

/**
 * Listener interface for monitoring MCP client operations.
 */
public interface McpClientListener {

    /**
     * Called before executing a tool.
     */
    default void beforeExecuteTool(McpCallContext context) {}

    /**
     * Called after executing a tool if the execution was successful, or if it resulted in an application-level error
     * (but not a protocol-level or communication error).
     */
    default void afterExecuteTool(McpCallContext context, ToolExecutionResult result, Map<String, Object> rawResult) {}

    /**
     * Called when a tool execution fails due to a protocol-level or communication error.
     */
    default void onExecuteToolError(McpCallContext context, Throwable error) {}

    /**
     * Called before getting a resource.
     */
    default void beforeResourceGet(McpCallContext context) {}

    /**
     * Called after getting a resource.
     */
    default void afterResourceGet(
            McpCallContext context, McpReadResourceResult result, Map<String, Object> rawResult) {}

    /**
     * Called when getting a resource fails.
     */
    default void onResourceGetError(McpCallContext context, Throwable error) {}

    /**
     * Called before getting a prompt.
     */
    default void beforePromptGet(McpCallContext context) {}

    /**
     * Called after getting a prompt.
     */
    default void afterPromptGet(McpCallContext context, McpGetPromptResult result, Map<String, Object> rawResult) {}

    /**
     * Called when getting a prompt fails.
     */
    default void onPromptGetError(McpCallContext context, Throwable error) {}
}
