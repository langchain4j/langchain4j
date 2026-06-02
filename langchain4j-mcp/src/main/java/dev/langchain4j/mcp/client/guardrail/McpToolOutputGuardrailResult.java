package dev.langchain4j.mcp.client.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.service.tool.ToolExecutionResult;

/**
 * The result of an {@link McpToolOutputGuardrail} validation.
 * Contains the (possibly transformed) {@link ToolExecutionResult}.
 */
@Experimental
public class McpToolOutputGuardrailResult {

    private final ToolExecutionResult toolExecutionResult;

    private McpToolOutputGuardrailResult(ToolExecutionResult toolExecutionResult) {
        this.toolExecutionResult = ensureNotNull(toolExecutionResult, "toolExecutionResult");
    }

    /**
     * Creates a successful result, optionally carrying a transformed {@link ToolExecutionResult}.
     *
     * @param toolExecutionResult the result to pass along (original or transformed)
     * @return a new guardrail result
     */
    public static McpToolOutputGuardrailResult success(ToolExecutionResult toolExecutionResult) {
        return new McpToolOutputGuardrailResult(toolExecutionResult);
    }

    /**
     * Returns the (possibly transformed) tool execution result.
     */
    public ToolExecutionResult toolExecutionResult() {
        return toolExecutionResult;
    }
}
