package dev.langchain4j.mcp.client.guardrail;

import dev.langchain4j.Experimental;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.jspecify.annotations.Nullable;

/**
 * The result of an {@link McpToolOutputGuardrail} validation.
 * On success, contains the (possibly transformed) {@link ToolExecutionResult}.
 * On failure, contains an error message that is returned to the LLM.
 */
@Experimental
public class McpToolOutputGuardrailResult {

    private final boolean success;
    private final ToolExecutionResult toolExecutionResult;
    private final String errorMessage;

    private McpToolOutputGuardrailResult(
            boolean success, @Nullable ToolExecutionResult toolExecutionResult, @Nullable String errorMessage) {
        this.success = success;
        this.toolExecutionResult = toolExecutionResult;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a successful result, optionally carrying a transformed {@link ToolExecutionResult}.
     *
     * @param toolExecutionResult the result to pass along (original or transformed)
     * @return a new guardrail result
     */
    public static McpToolOutputGuardrailResult success(ToolExecutionResult toolExecutionResult) {
        return new McpToolOutputGuardrailResult(true, toolExecutionResult, null);
    }

    /**
     * Creates a failure result indicating that the tool output should be rejected.
     *
     * @param errorMessage the error message to return to the LLM
     * @return a new guardrail result
     */
    public static McpToolOutputGuardrailResult failure(String errorMessage) {
        return new McpToolOutputGuardrailResult(false, null, errorMessage);
    }

    /**
     * Returns {@code true} if validation passed.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the (possibly transformed) tool execution result, or {@code null} if validation failed.
     */
    @Nullable
    public ToolExecutionResult toolExecutionResult() {
        return toolExecutionResult;
    }

    /**
     * Returns the error message, or {@code null} if validation passed.
     */
    @Nullable
    public String errorMessage() {
        return errorMessage;
    }
}
