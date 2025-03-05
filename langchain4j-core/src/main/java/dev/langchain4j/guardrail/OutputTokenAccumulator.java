package dev.langchain4j.guardrail;

/**
 * Interface to accumulate tokens when output guardrails are applied on streamed responses.
 */
public interface OutputTokenAccumulator {
    /**
     * Accumulate tokens before applying the guardrails. The guardrails are invoked for each item emitted by the produce
     * {@link Multi}.
     * <p>
     * If the returned {@link Multi} emits an error, the guardrail chain is not called and the error is propagated. If
     * the returned {@link Multi} completes, the guardrail chain is called with the remaining accumulated tokens.
     *
     * @param tokens
     *            the input token stream
     *
     * @return the Multi producing the accumulated tokens
     */
    // @TODO Need to figure this out!
    // Will work on the streaming case after the synchronous case
    // Multi<String> accumulate(Multi<String> tokens);
}
