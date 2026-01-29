package dev.langchain4j.mcp.client;

import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.Map;

public interface McpClientListener {

    default void beforeExecuteTool(McpCallContext context) {}

    default void afterExecuteTool(McpCallContext context, ToolExecutionResult result, Map<String, Object> rawResult) {}

    default void onExecuteToolError(McpCallContext context, Throwable error) {}

    default void beforeResourceGet(McpCallContext context) {}

    default void afterResourceGet(
            McpCallContext context, McpReadResourceResult result, Map<String, Object> rawResult) {}

    default void onResourceGetError(McpCallContext context, Throwable error) {}

    default void beforePromptGet(McpCallContext context) {}

    default void afterPromptGet(McpCallContext context, McpGetPromptResult result, Map<String, Object> rawResult) {}

    default void onPromptGetError(McpCallContext context, Throwable error) {}
}
