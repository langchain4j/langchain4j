package dev.langchain4j.audit.api.event;

import dev.langchain4j.guardrail.Guardrail;
import dev.langchain4j.guardrail.GuardrailRequest;
import dev.langchain4j.guardrail.GuardrailResult;

/**
 * Represents an event that is executed when a guardrail validation occurs.
 * This interface serves as a marker for events that contain both parameters
 * and results associated with guardrail validation.
 *
 * @param <P> the type of guardrail parameters used in the validation process
 * @param <R> the type of guardrail result produced by the validation process
 * @param <G> the type of guardrail class used in the validation process
 */
public interface GuardrailExecutedEvent<
                P extends GuardrailRequest<P>, R extends GuardrailResult<R>, G extends Guardrail<P, R>>
        extends LLMInteractionEvent {

    /**
     * Retrieves the request used for input guardrail validation.
     *
     * @return the parameters containing user message, memory, augmentation result, user message template,
     *         and associated variables for input guardrail validation.
     */
    P request();

    /**
     * Retrieves the result of the input guardrail validation process.
     *
     * @return the result of the input guardrail validation, including the validation outcome
     *         and any associated failures, if present.
     */
    R result();

    /**
     * Retrieves the guardrail class associated with the validation process.
     *
     * @return the guardrail class that implements the logic for validating
     *         the interaction between user and LLM, represented as an instance
     *         of the type extending {@code Guardrail<P, R>}.
     */
    Class<G> guardrailClass();

    abstract class GuardrailExecutedEventBuilder<
                    P extends GuardrailRequest<P>,
                    R extends GuardrailResult<R>,
                    G extends Guardrail<P, R>,
                    T extends GuardrailExecutedEvent<P, R, G>>
            extends Builder<T> {

        private P request;
        private R result;
        private Class<G> guardrailClass;

        protected GuardrailExecutedEventBuilder() {}

        protected GuardrailExecutedEventBuilder(T src) {
            super(src);
            request(src.request());
            result(src.result());
            guardrailClass(src.guardrailClass());
        }

        public Class<G> getGuardrailClass() {
            return guardrailClass;
        }

        public P getRequest() {
            return request;
        }

        public R getResult() {
            return result;
        }

        public GuardrailExecutedEventBuilder<P, R, G, T> request(P request) {
            this.request = request;
            return this;
        }

        public GuardrailExecutedEventBuilder<P, R, G, T> result(R result) {
            this.result = result;
            return this;
        }

        public GuardrailExecutedEventBuilder<P, R, G, T> interactionSource(InteractionSource interactionSource) {
            return (GuardrailExecutedEventBuilder<P, R, G, T>) super.interactionSource(interactionSource);
        }

        public <C extends G> GuardrailExecutedEventBuilder<P, R, G, T> guardrailClass(Class<C> guardrailClass) {
            this.guardrailClass = (Class<G>) guardrailClass;
            return this;
        }
    }
}
