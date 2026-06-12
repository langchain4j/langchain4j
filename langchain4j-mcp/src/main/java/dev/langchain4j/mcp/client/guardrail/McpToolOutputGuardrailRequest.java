package dev.langchain4j.mcp.client.guardrail;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.jspecify.annotations.Nullable;

/**
 * The context provided to an {@link McpToolOutputGuardrail} after tool execution.
 *
 * @param toolExecutionRequest the tool execution request that was sent to the MCP server
 * @param toolExecutionResult the result returned by the MCP server (possibly transformed by a previous guardrail)
 * @param invocationContext the AI service invocation context, may be {@code null}
 * @param mcpClient the MCP client that executed the tool
 */
@Experimental
public record McpToolOutputGuardrailRequest(
        ToolExecutionRequest toolExecutionRequest,
        ToolExecutionResult toolExecutionResult,
        @Nullable InvocationContext invocationContext,
        McpClient mcpClient) {}
