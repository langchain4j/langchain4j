package dev.langchain4j.guardrail;

/**
 * Represents the parameter passed to {@link Guardrail#validate(GuardrailRequest)}} in order to validate an interaction
 * between a user and the LLM.
 */
public sealed interface GuardrailRequest<P extends GuardrailRequest<P>>
        permits InputGuardrailRequest, OutputGuardrailRequest {

    /**
     * Retrieves the common parameters that are shared across guardrail checks.
     *
     * @return an instance of {@code GuardrailRequestParams} containing shared parameters such as chat memory,
     *         user message template, and additional variables.
     */
    GuardrailRequestParams requestParams();

    /**
     * Recreate this guardrail param with the given input or output text.
     *
     * @param text
     *            The text of the rewritten param.
     *
     * @return A clone of this guardrail params with the given input or output text.
     */
    P withText(String text);
}
