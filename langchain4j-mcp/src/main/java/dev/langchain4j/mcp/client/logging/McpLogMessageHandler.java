package dev.langchain4j.mcp.client.logging;

/**
 * A handler that decides what to do with received log messages from an MCP
 * server.
 */
public interface McpLogMessageHandler {

    void handleLogMessage(McpLogMessage message);
}
