package dev.langchain4j.mcp.client.guardrail;

import dev.langchain4j.Experimental;
import org.jspecify.annotations.Nullable;

/**
 * The result of an {@link McpToolInputGuardrail} validation.
 */
@Experimental
public class McpToolInputGuardrailResult {

    private static final McpToolInputGuardrailResult SUCCESS = new McpToolInputGuardrailResult(true, null);

    private final boolean success;
    private final String errorMessage;

    private McpToolInputGuardrailResult(boolean success, @Nullable String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a successful result indicating that the tool call is allowed.
     */
    public static McpToolInputGuardrailResult success() {
        return SUCCESS;
    }

    /**
     * Creates a failure result indicating that the tool call should be rejected.
     *
     * @param errorMessage the error message to return to the LLM
     */
    public static McpToolInputGuardrailResult failure(String errorMessage) {
        return new McpToolInputGuardrailResult(false, errorMessage);
    }

    /**
     * Returns {@code true} if validation passed.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the error message, or {@code null} if validation passed.
     */
    @Nullable
    public String errorMessage() {
        return errorMessage;
    }
}
