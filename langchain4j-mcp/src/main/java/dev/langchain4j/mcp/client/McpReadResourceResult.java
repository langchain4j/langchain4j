package dev.langchain4j.mcp.client;

import java.util.List;

/**
 * The 'ReadResourceResult' object from the MCP protocol schema.
 */
public record McpReadResourceResult(List<McpResourceContents> contents) {}
