package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;

/**
 * The request passed to a {@link ToolOutputGuardrail}.
 * <p>
 * {@link #executionRequest()} is the tool call as it was actually executed, so it reflects any rewriting
 * done by the {@link ToolInputGuardrail}s.
 *
 * @since 1.19.0
 */
@Experimental
public record ToolOutputGuardrailRequest(
        ToolExecutionResult executionResult,
        ToolExecutionRequest executionRequest,
        ToolMetadata toolMetadata,
        InvocationContext invocationContext)
        implements ToolGuardrailRequest<ToolOutputGuardrailRequest> {

    public ToolOutputGuardrailRequest {
        ensureNotNull(executionResult, "executionResult");
        ensureNotNull(executionRequest, "executionRequest");
    }

    /**
     * Returns a copy of this request carrying the given result, so a chain of guardrails can each see
     * what the previous one rewrote.
     */
    public ToolOutputGuardrailRequest with(ToolExecutionResult executionResult) {
        return new ToolOutputGuardrailRequest(executionResult, executionRequest, toolMetadata, invocationContext);
    }

    /**
     * The text the LLM would see.
     */
    public String resultText() {
        return executionResult.resultText();
    }

    /**
     * Whether the tool execution failed.
     */
    public boolean isError() {
        return executionResult.isError();
    }
}
