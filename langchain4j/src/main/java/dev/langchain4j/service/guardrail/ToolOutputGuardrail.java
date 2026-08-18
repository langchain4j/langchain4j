package dev.langchain4j.service.guardrail;

import dev.langchain4j.Experimental;
import dev.langchain4j.service.tool.ToolExecutionResult;

/**
 * Validates the result of a tool after it ran, before the LLM sees it.
 * <p>
 * Useful for redacting or truncating what a tool returns, and for containing prompt injection: tool
 * results are the main route by which untrusted content reaches the model.
 *
 * @since 1.19.0
 */
@Experimental
public interface ToolOutputGuardrail extends ToolGuardrail<ToolOutputGuardrailRequest, ToolOutputGuardrailResult> {

    @Override
    ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request);

    /**
     * The result is handed back to the LLM unchanged.
     */
    default ToolOutputGuardrailResult success() {
        return ToolOutputGuardrailResult.success();
    }

    /**
     * The given result replaces the one the tool produced.
     * Subsequent guardrails in the chain see the rewritten result.
     */
    default ToolOutputGuardrailResult successWith(ToolExecutionResult rewrittenResult) {
        return ToolOutputGuardrailResult.successWith(rewrittenResult);
    }

    /**
     * The result is rejected and replaced by an error result handed back to the LLM.
     */
    default ToolOutputGuardrailResult failure(String message) {
        return new ToolOutputGuardrailResult(new ToolOutputGuardrailResult.Failure(message), false);
    }

    default ToolOutputGuardrailResult failure(String message, Throwable cause) {
        return new ToolOutputGuardrailResult(new ToolOutputGuardrailResult.Failure(message, cause), false);
    }

    /**
     * The result is rejected and the whole invocation is aborted with a {@link ToolGuardrailException}.
     */
    default ToolOutputGuardrailResult fatal(String message) {
        return new ToolOutputGuardrailResult(new ToolOutputGuardrailResult.Failure(message), true);
    }

    default ToolOutputGuardrailResult fatal(String message, Throwable cause) {
        return new ToolOutputGuardrailResult(new ToolOutputGuardrailResult.Failure(message, cause), true);
    }
}
