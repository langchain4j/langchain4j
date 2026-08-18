package dev.langchain4j.service.guardrail;

import dev.langchain4j.Experimental;

/**
 * A guardrail around the execution of a single tool.
 * <p>
 * Whereas {@link dev.langchain4j.guardrail.InputGuardrail} and {@link dev.langchain4j.guardrail.OutputGuardrail}
 * guard what goes into and comes out of the model, tool guardrails guard what the model is allowed to
 * <em>do</em>: a {@link ToolInputGuardrail} sees a tool call and its arguments before the tool runs and can
 * rewrite or refuse it, and a {@link ToolOutputGuardrail} sees the result before it is handed back to the LLM.
 * <p>
 * This is a separate hierarchy from {@link dev.langchain4j.guardrail.Guardrail} on purpose: a tool guardrail
 * has no text to rewrite, so it cannot satisfy {@link dev.langchain4j.guardrail.GuardrailRequest#withText(String)}.
 *
 * @param <P> the request type this guardrail validates
 * @param <R> the result type this guardrail produces
 * @see ToolInputGuardrail
 * @see ToolOutputGuardrail
 * @since 1.19.0
 */
@Experimental
public interface ToolGuardrail<P extends ToolGuardrailRequest<P>, R extends ToolGuardrailResult<R>> {

    /**
     * Returns the name of this guardrail, used in failure messages and observability.
     */
    default String name() {
        return getClass().getSimpleName();
    }

    /**
     * Validates the given request.
     */
    R validate(P request);
}
