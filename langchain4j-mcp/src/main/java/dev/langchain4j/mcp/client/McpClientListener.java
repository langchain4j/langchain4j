package dev.langchain4j.mcp.client;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.logging.McpLogMessage;
import dev.langchain4j.mcp.client.progress.McpProgressNotification;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.List;
import java.util.Map;

/**
 * Listener interface for monitoring MCP client operations.
 */
public interface McpClientListener {

    // ========== Client-initiated: initialize ==========

    /**
     * Called before sending the initialize request to the MCP server.
     */
    default void beforeInitialize(McpCallContext context) {}

    /**
     * Called after the initialize request completes successfully.
     */
    default void afterInitialize(McpCallContext context) {}

    /**
     * Called when the initialize request fails.
     */
    default void onInitializeError(McpCallContext context, Throwable error) {}

    // ========== Client-initiated: tools/call ==========

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

    // ========== Client-initiated: tools/list ==========

    /**
     * Called before listing tools from the MCP server.
     */
    default void beforeToolsList(McpCallContext context) {}

    /**
     * Called after listing tools from the MCP server completes successfully.
     */
    default void afterToolsList(McpCallContext context, List<ToolSpecification> tools) {}

    /**
     * Called when listing tools from the MCP server fails.
     */
    default void onToolsListError(McpCallContext context, Throwable error) {}

    // ========== Client-initiated: resources/read ==========

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

    // ========== Client-initiated: resources/list ==========

    /**
     * Called before listing resources from the MCP server.
     */
    default void beforeResourcesList(McpCallContext context) {}

    /**
     * Called after listing resources from the MCP server completes successfully.
     */
    default void afterResourcesList(McpCallContext context, List<McpResource> resources) {}

    /**
     * Called when listing resources from the MCP server fails.
     */
    default void onResourcesListError(McpCallContext context, Throwable error) {}

    // ========== Client-initiated: resources/templates/list ==========

    /**
     * Called before listing resource templates from the MCP server.
     */
    default void beforeResourceTemplatesList(McpCallContext context) {}

    /**
     * Called after listing resource templates from the MCP server completes successfully.
     */
    default void afterResourceTemplatesList(McpCallContext context, List<McpResourceTemplate> templates) {}

    /**
     * Called when listing resource templates from the MCP server fails.
     */
    default void onResourceTemplatesListError(McpCallContext context, Throwable error) {}

    // ========== Client-initiated: resources/subscribe ==========

    /**
     * Called before subscribing to a resource.
     */
    default void beforeResourceSubscribe(McpCallContext context) {}

    /**
     * Called after subscribing to a resource completes successfully.
     */
    default void afterResourceSubscribe(McpCallContext context) {}

    /**
     * Called when subscribing to a resource fails.
     */
    default void onResourceSubscribeError(McpCallContext context, Throwable error) {}

    // ========== Client-initiated: resources/unsubscribe ==========

    /**
     * Called before unsubscribing from a resource.
     */
    default void beforeResourceUnsubscribe(McpCallContext context) {}

    /**
     * Called after unsubscribing from a resource completes successfully.
     */
    default void afterResourceUnsubscribe(McpCallContext context) {}

    /**
     * Called when unsubscribing from a resource fails.
     */
    default void onResourceUnsubscribeError(McpCallContext context, Throwable error) {}

    // ========== Client-initiated: prompts/get ==========

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

    // ========== Client-initiated: prompts/list ==========

    /**
     * Called before listing prompts from the MCP server.
     */
    default void beforePromptsList(McpCallContext context) {}

    /**
     * Called after listing prompts from the MCP server completes successfully.
     */
    default void afterPromptsList(McpCallContext context, List<McpPrompt> prompts) {}

    /**
     * Called when listing prompts from the MCP server fails.
     */
    default void onPromptsListError(McpCallContext context, Throwable error) {}

    // ========== Client-initiated: ping ==========

    /**
     * Called before sending a ping request to the MCP server.
     */
    default void beforePing(McpCallContext context) {}

    /**
     * Called after the ping request completes successfully (the server has responded with a pong).
     */
    default void afterPing(McpCallContext context) {}

    /**
     * Called when the ping request fails (the server didn't send a pong in time or some
     * transport-level issue occurred).
     */
    default void onPingError(McpCallContext context, Throwable error) {}

    // ========== Client-initiated notification: notifications/roots/list_changed ==========

    /**
     * Called after the client sends a {@code notifications/roots/list_changed}
     * notification to the server.
     */
    default void onRootsListChanged(McpCallContext context) {}

    // ========== Server-initiated notifications ==========

    /**
     * Called when the server sends a {@code notifications/tools/list_changed} notification.
     */
    default void onNotificationToolsListChanged() {}

    /**
     * Called when the server sends a {@code notifications/resources/list_changed} notification.
     */
    default void onNotificationResourcesListChanged() {}

    /**
     * Called when the server sends a {@code notifications/prompts/list_changed} notification.
     */
    default void onNotificationPromptsListChanged() {}

    /**
     * Called when the server sends a {@code notifications/resources/updated} notification.
     */
    default void onNotificationResourceUpdated(String uri) {}

    /**
     * Called when the server sends a {@code notifications/message} (log message) notification.
     */
    default void onNotificationMessage(McpLogMessage message) {}

    /**
     * Called when the server sends a {@code notifications/progress} notification.
     */
    default void onNotificationProgress(McpProgressNotification notification) {}

    // ========== Server-initiated requests ==========

    /**
     * Called when the server sends a {@code ping} request and the client responds.
     */
    default void onServerPing() {}

    /**
     * Called when the server sends a {@code roots/list} request and the client responds.
     */
    default void onServerRootsList() {}
}
