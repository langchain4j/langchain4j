package dev.langchain4j.mcp.client;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import org.jspecify.annotations.Nullable;

/**
 * Context information for any invocation made towards an MCP server.
 *
 * It contains the AI service invocation context when this is during
 * an AI service invocation (in other cases, the invocation context is null).
 */
public record McpCallContext(@Nullable InvocationContext invocationContext, McpClientMessage message) {}
