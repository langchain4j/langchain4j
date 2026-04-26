package dev.langchain4j.mcp.client;

import dev.langchain4j.mcp.client.logging.McpLogMessage;
import dev.langchain4j.mcp.client.progress.McpProgressNotification;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.Map;

/**
 * Listener interface for monitoring MCP client operations.
 * <p>
 * Covers both client-initiated operations (tool execution, resource reads, prompt retrieval)
 * and server-initiated notifications (progress, resource updates, list changes, etc.).
 * <p>
 * All methods have default no-op implementations for backward compatibility.
 */
public interface McpClientListener {

    // ==================== Tool Execution ====================

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

    // ==================== Resource Operations ====================

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

    // ==================== Prompt Operations ====================

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

    // ==================== Server-Initiated Notifications ====================

    /**
     * Called when the server sends a ping request.
     * The client automatically responds with a pong; this callback is for observability.
     */
    default void onPing() {}

    /**
     * Called when the server responds to a client-initiated ping.
     * This marks the end of a successful health check round-trip.
     */
    default void onPong() {}

    /**
     * Called when the server sends a tools/list_changed notification,
     * indicating the available tool list may have changed.
     */
    default void onToolsListChanged() {}

    /**
     * Called when the server sends a resources/list_changed notification,
     * indicating the available resource list may have changed.
     */
    default void onResourcesListChanged() {}

    /**
     * Called when the server sends a prompts/list_changed notification,
     * indicating the available prompt list may have changed.
     */
    default void onPromptsListChanged() {}

    /**
     * Called when the server sends a resources/updated notification for a subscribed resource.
     * @param uri the URI of the updated resource
     */
    default void onResourceUpdated(String uri) {}

    /**
     * Called when the server sends a progress notification during a long-running tool execution.
     * @param notification the progress notification from the server
     */
    default void onProgress(McpProgressNotification notification) {}

    /**
     * Called when the server sends a log message notification.
     * @param message the log message from the server
     */
    default void onLogMessage(McpLogMessage message) {}

    /**
     * Called when the client receives an initialized notification from the server
     * (sent by the server after it has initialized).
     */
    default void onInitialized() {}

    /**
     * Called when the server requests the roots list.
     * The client automatically responds with the current roots; this callback is for observability.
     */
    default void onRootsListRequested() {}
}
