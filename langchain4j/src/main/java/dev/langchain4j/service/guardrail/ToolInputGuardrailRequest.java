package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;

/**
 * The request passed to a {@link ToolInputGuardrail}.
 *
 * @since 1.19.0
 */
@Experimental
public record ToolInputGuardrailRequest(
        ToolExecutionRequest executionRequest, ToolMetadata toolMetadata, InvocationContext invocationContext)
        implements ToolGuardrailRequest<ToolInputGuardrailRequest> {

    public ToolInputGuardrailRequest {
        ensureNotNull(executionRequest, "executionRequest");
    }

    /**
     * Returns a copy of this request carrying the given tool call, so a chain of guardrails can each see
     * what the previous one rewrote.
     */
    public ToolInputGuardrailRequest with(ToolExecutionRequest executionRequest) {
        return new ToolInputGuardrailRequest(executionRequest, toolMetadata, invocationContext);
    }
}
