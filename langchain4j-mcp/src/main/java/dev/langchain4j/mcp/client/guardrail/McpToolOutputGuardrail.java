package dev.langchain4j.mcp.client.guardrail;

import dev.langchain4j.Experimental;

/**
 * A guardrail that validates or transforms the result of an MCP tool execution.
 * <p>
 * Return {@link McpToolOutputGuardrailResult#success(dev.langchain4j.service.tool.ToolExecutionResult)}
 * with the original or a transformed result, or
 * {@link McpToolOutputGuardrailResult#failure(String)} to reject it — the error message
 * is returned to the LLM.
 * <p>
 * When multiple output guardrails are configured, they are executed in order.
 * Each guardrail receives the (possibly transformed) result from the previous one.
 * The first guardrail to return a failure stops the chain.
 */
@Experimental
@FunctionalInterface
public interface McpToolOutputGuardrail {

    /**
     * Validates or transforms the tool execution result.
     *
     * @param request the guardrail request containing the tool execution result and context
     * @return a result containing the original or transformed tool execution result, or a failure
     */
    McpToolOutputGuardrailResult validate(McpToolOutputGuardrailRequest request);
}
