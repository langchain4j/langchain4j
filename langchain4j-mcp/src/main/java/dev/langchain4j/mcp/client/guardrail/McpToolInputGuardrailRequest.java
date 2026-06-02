package dev.langchain4j.mcp.client.guardrail;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import org.jspecify.annotations.Nullable;

/**
 * The context provided to an {@link McpToolInputGuardrail} before tool execution.
 *
 * @param toolExecutionRequest the tool execution request about to be sent to the MCP server
 * @param invocationContext the AI service invocation context, may be {@code null}
 * @param mcpClient the MCP client that will execute the tool
 */
@Experimental
public record McpToolInputGuardrailRequest(
        ToolExecutionRequest toolExecutionRequest,
        @Nullable InvocationContext invocationContext,
        McpClient mcpClient) {}
