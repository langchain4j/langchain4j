package dev.langchain4j.mcp.client.guardrail;

import dev.langchain4j.Experimental;

/**
 * A guardrail that validates or transforms the result of an MCP tool execution.
 * <p>
 * If validation passes, return a {@link McpToolOutputGuardrailResult} containing
 * the original or a transformed {@link dev.langchain4j.service.tool.ToolExecutionResult}.
 * If validation fails, throw {@link McpToolGuardrailException} — the exception message
 * is returned to the LLM as an error.
 * <p>
 * When multiple output guardrails are configured, they are executed in order.
 * Each guardrail receives the (possibly transformed) result from the previous one.
 * The first guardrail to throw stops the chain.
 */
@Experimental
@FunctionalInterface
public interface McpToolOutputGuardrail {

    /**
     * Validates or transforms the tool execution result.
     *
     * @param request the guardrail request containing the tool execution result and context
     * @return a result containing the original or transformed {@link dev.langchain4j.service.tool.ToolExecutionResult}
     * @throws McpToolGuardrailException if the tool result should be rejected
     */
    McpToolOutputGuardrailResult validate(McpToolOutputGuardrailRequest request) throws McpToolGuardrailException;
}
