package dev.langchain4j.observability.api.event;

import dev.langchain4j.guardrail.Guardrail;
import dev.langchain4j.guardrail.GuardrailRequest;
import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import java.time.Duration;

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
        extends AiServiceEvent {

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

    /**
     * Retrieves the logical guardrail name exposed to observability consumers.
     *
     * <p>By default this returns the simple name of {@link #guardrailClass()},
     * which matches the behavior before the field was introduced. When a decorator
     * / wrapper guardrail supplies a logical name via {@link Guardrail#name()},
     * the executor propagates that name into the event and {@code guardrailName()}
     * returns it. This allows observability systems and audit logs to see the
     * logical guardrail identity (e.g. {@code "ProfanityFilter"}) rather than the
     * adapter class (e.g. {@code "InputGuardrailAdapter"}) — see issue #4938.
     *
     * @return the logical guardrail name, or the simple class name if no
     *         logical name was propagated by the executor
     */
    default String guardrailName() {
        Class<G> clazz = guardrailClass();
        return clazz != null ? clazz.getSimpleName() : null;
    }

    /**
     * Retrieves the duration of the guardrail execution.
     *
     * @return the duration of the guardrail validation process.
     */
    Duration duration();

    abstract class GuardrailExecutedEventBuilder<
                    P extends GuardrailRequest<P>,
                    R extends GuardrailResult<R>,
                    G extends Guardrail<P, R>,
                    T extends GuardrailExecutedEvent<P, R, G>>
            extends Builder<T> {

        private P request;
        private R result;
        private Class<G> guardrailClass;
        private Duration duration;
        private String guardrailName;

        protected GuardrailExecutedEventBuilder() {}

        protected GuardrailExecutedEventBuilder(T src) {
            super(src);
            request(src.request());
            result(src.result());
            guardrailClass(src.guardrailClass());
            duration(src.duration());
            guardrailName(src.guardrailName());
        }

        public Class<G> guardrailClass() {
            return guardrailClass;
        }

        public P request() {
            return request;
        }

        public R result() {
            return result;
        }

        public Duration duration() {
            return duration;
        }

        /**
         * Returns the logical guardrail name that was set via
         * {@link #guardrailName(String)}, or {@code null} if none was set.
         * When {@code null}, {@link GuardrailExecutedEvent#guardrailName()} falls
         * back to the simple name of {@link #guardrailClass()}.
         */
        public String guardrailName() {
            return guardrailName;
        }

        public GuardrailExecutedEventBuilder<P, R, G, T> request(P request) {
            this.request = request;
            return this;
        }

        public GuardrailExecutedEventBuilder<P, R, G, T> result(R result) {
            this.result = result;
            return this;
        }

        public GuardrailExecutedEventBuilder<P, R, G, T> invocationContext(InvocationContext invocationContext) {
            return (GuardrailExecutedEventBuilder<P, R, G, T>) super.invocationContext(invocationContext);
        }

        public <C extends G> GuardrailExecutedEventBuilder<P, R, G, T> guardrailClass(Class<C> guardrailClass) {
            this.guardrailClass = (Class<G>) guardrailClass;
            return this;
        }

        public GuardrailExecutedEventBuilder<P, R, G, T> duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        /**
         * Sets the logical guardrail name to be exposed via
         * {@link GuardrailExecutedEvent#guardrailName()}. Callers normally pass
         * {@code guardrail.name()} so that decorator / wrapper guardrails can
         * expose the underlying guardrail identity (issue #4938).
         */
        public GuardrailExecutedEventBuilder<P, R, G, T> guardrailName(String guardrailName) {
            this.guardrailName = guardrailName;
            return this;
        }
    }
}
