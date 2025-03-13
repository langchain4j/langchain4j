package dev.langchain4j.mcp.client;

import java.util.List;

/**
 * The 'GetPromptResult' object from the MCP protocol schema.
 */
public record MgpGetPromptResult(String description, List<McpPromptMessage> messages) {}
