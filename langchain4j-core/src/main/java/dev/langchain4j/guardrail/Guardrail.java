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
     * @param params
     *            The parameters of the request or the response to be validated
     *
     * @return The result of the validation
     */
    R validate(P params);
}
