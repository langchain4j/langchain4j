package dev.langchain4j.mcp.client;

/**
 * The 'Resource' object from the MCP protocol schema.
 */
public record McpResource(String uri, String name, String description, String mimeType) {}
