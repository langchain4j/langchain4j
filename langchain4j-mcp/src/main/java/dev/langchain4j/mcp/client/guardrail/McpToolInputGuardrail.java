package dev.langchain4j.mcp.client.guardrail;

import dev.langchain4j.Experimental;

/**
 * A guardrail that validates a tool execution request before it is sent to the MCP server.
 * <p>
 * Return {@link McpToolInputGuardrailResult#success()} to allow the call, or
 * {@link McpToolInputGuardrailResult#failure(String)} to reject it — the error message
 * is returned to the LLM.
 * <p>
 * When multiple input guardrails are configured, they are executed in order.
 * The first guardrail to return a failure stops the chain.
 */
@Experimental
@FunctionalInterface
public interface McpToolInputGuardrail {

    /**
     * Validates the tool execution request.
     *
     * @param request the guardrail request containing the tool execution request and context
     * @return the validation result
     */
    McpToolInputGuardrailResult validate(McpToolInputGuardrailRequest request);
}
