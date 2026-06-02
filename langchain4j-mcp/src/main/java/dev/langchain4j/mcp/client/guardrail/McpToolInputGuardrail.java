package dev.langchain4j.mcp.client.guardrail;

import dev.langchain4j.Experimental;

/**
 * A guardrail that validates a tool execution request before it is sent to the MCP server.
 * <p>
 * If validation passes, the method returns normally.
 * If validation fails, the method throws {@link McpToolGuardrailException},
 * and the tool call is not executed — the exception message is returned to the LLM as an error.
 * <p>
 * When multiple input guardrails are configured, they are executed in order.
 * The first guardrail to throw stops the chain.
 */
@Experimental
@FunctionalInterface
public interface McpToolInputGuardrail {

    /**
     * Validates the tool execution request.
     *
     * @param request the guardrail request containing the tool execution request and context
     * @throws McpToolGuardrailException if the tool call should be rejected
     */
    void validate(McpToolInputGuardrailRequest request) throws McpToolGuardrailException;
}
