package dev.langchain4j.mcp.client;

/**
 * The 'ResourceTemplate' object from the MCP protocol schema.
 */
public record McpResourceTemplate(String uriTemplate, String name, String description, String mimeType) {}
