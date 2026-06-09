package dev.langchain4j.guardrail;

/**
 * A guardrail is a rule that is applied when interacting with an LLM either to the input (the user message) or to the
 * output of the model to ensure that they are safe and meet the expectations of the model.
 *
 * @param <P>
 *            The type of the {@link GuardrailRequest}
 * @param <R>
 *            The type of the {@link GuardrailResult}
 */
public interface Guardrail<P extends GuardrailRequest, R extends GuardrailResult<R>> {
    /**
     * Validate the interaction between the model and the user in one of the two directions.
     *
     * @param request
     *            The parameters of the request or the response to be validated
     *
     * @return The result of the validation
     */
    R validate(P request);

    /**
     * Returns a human-readable name for this guardrail.
     *
     * <p>By default this returns the simple class name of the implementing guardrail
     * (e.g. {@code "MyInputGuardrail"}), which is sufficient for observability and
     * logging when a guardrail is used directly.
     *
     * <p>Decorator / wrapper guardrails (those that hold a delegate and forward
     * {@link #validate(GuardrailRequest)} to it) should override this method to
     * return the underlying guardrail's name instead of the wrapper's name. This
     * ensures observability systems and audit logs see the logical guardrail
     * identity, not the adapter class (issue #4938).
     *
     * @return the logical guardrail name exposed to observability consumers
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
