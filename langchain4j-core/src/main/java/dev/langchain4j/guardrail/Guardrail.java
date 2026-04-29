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
     * Returns the logical name of this guardrail, used for observability and reporting.
     *
     * <p>The default implementation returns the simple class name of the guardrail. Decorator or
     * adapter implementations should override this method to return the name of the underlying
     * guardrail they wrap, so that observability systems (e.g. {@code GuardrailExecutedEvent})
     * correctly identify the logical guardrail rather than the wrapper.
     *
     * <p>Example:
     * <pre>{@code
     * public class PromptInjectionGuardrailAdapter implements InputGuardrail {
     *     private final PromptInjectionGuardrail delegate;
     *
     *     @Override
     *     public String name() {
     *         return delegate.name();
     *     }
     * }
     * }</pre>
     *
     * @return the logical name of this guardrail; never {@code null}
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
