package dev.langchain4j.observability.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.guardrail.Guardrail;
import dev.langchain4j.guardrail.GuardrailRequest;
import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.observability.api.event.GuardrailExecutedEvent;
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
public abstract class DefaultGuardrailExecutedEvent<
                P extends GuardrailRequest<P>,
                R extends GuardrailResult<R>,
                G extends Guardrail<P, R>,
                E extends GuardrailExecutedEvent<P, R, G>>
        extends AbstractAiServiceEvent implements GuardrailExecutedEvent<P, R, G> {

    private final P request;
    private final R result;
    private final Class<G> guardrailClass;
    private final Duration duration;
    private final String guardrailName;

    protected DefaultGuardrailExecutedEvent(GuardrailExecutedEventBuilder<P, R, G, E> builder) {
        super(builder);
        this.request = ensureNotNull(builder.request(), "request");
        this.result = ensureNotNull(builder.result(), "result");
        this.guardrailClass = ensureNotNull(builder.guardrailClass(), "guardrailClass");
        this.duration = ensureNotNull(builder.duration(), "duration");
        // Optional: when null, GuardrailExecutedEvent#guardrailName() falls back to
        // the simple name of guardrailClass().
        this.guardrailName = builder.guardrailName();
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

    @Override
    public Duration duration() {
        return this.duration;
    }

    @Override
    public String guardrailName() {
        if (this.guardrailName != null) {
            return this.guardrailName;
        }
        // Fallback: use the class simple name so existing call sites that do not
        // set a logical name continue to see a sensible value.
        return this.guardrailClass != null ? this.guardrailClass.getSimpleName() : null;
    }
}
