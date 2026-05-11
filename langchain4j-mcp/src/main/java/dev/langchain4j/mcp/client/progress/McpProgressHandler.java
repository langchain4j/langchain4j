package dev.langchain4j.mcp.client.progress;

/**
 * Handler for MCP progress notifications.
 * Implement this interface to receive progress updates from the MCP server
 * during long-running tool executions.
 */
public interface McpProgressHandler {

    /**
     * Called when a progress notification is received from the MCP server.
     *
     * @param notification the progress notification
     */
    void onProgress(McpProgressNotification notification);
}
