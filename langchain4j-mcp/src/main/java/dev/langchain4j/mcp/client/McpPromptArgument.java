package dev.langchain4j.mcp.client;

/**
 * The 'PromptArgument' object from the MCP protocol schema.
 */
public record McpPromptArgument(String name, String description, boolean required) {}
