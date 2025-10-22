package dev.langchain4j.observability.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.guardrail.Guardrail;
import dev.langchain4j.guardrail.GuardrailRequest;
import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.observability.api.event.GuardrailExecutedEvent;

/**
 * Represents an event that is executed when a guardrail validation occurs.
 * This interface serves as a marker for events that contain both parameters
 * and results associated with guardrail validation.
 *
 * @param <P> the type of guardrail parameters used in the validation process
 * @param <R> the type of guardrail result produced by the validation process
 * @param <G> the type of guardrail class used in the validation process
 */
public abstract class DefaultGuardrailExecutedEvent<
                P extends GuardrailRequest<P>,
                R extends GuardrailResult<R>,
                G extends Guardrail<P, R>,
                E extends GuardrailExecutedEvent<P, R, G>>
        extends AbstractAiServiceEvent implements GuardrailExecutedEvent<P, R, G> {

    private final P request;
    private final R result;
    private final Class<G> guardrailClass;

    protected DefaultGuardrailExecutedEvent(GuardrailExecutedEventBuilder<P, R, G, E> builder) {
        super(builder);
        this.request = ensureNotNull(builder.request(), "request");
        this.result = ensureNotNull(builder.result(), "result");
        this.guardrailClass = ensureNotNull(builder.guardrailClass(), "guardrailClass");
    }

    @Override
    public P request() {
        return this.request;
    }

    @Override
    public R result() {
        return this.result;
    }

    @Override
    public Class<G> guardrailClass() {
        return this.guardrailClass;
    }
}
