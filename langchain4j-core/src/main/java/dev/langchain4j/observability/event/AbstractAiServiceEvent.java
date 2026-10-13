package dev.langchain4j.observability.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import java.time.Instant;

public abstract class AbstractAiServiceEvent implements AiServiceEvent {
    private final InvocationContext invocationContext;
    private final Instant timestamp;

    protected AbstractAiServiceEvent(Builder<?> builder) {
        ensureNotNull(builder, "builder");
        this.invocationContext = ensureNotNull(builder.invocationContext(), "invocationContext");
        this.timestamp = builder.timestamp() != null ? builder.timestamp() : Instant.now();
    }

    @Override
    public Instant timestamp() {
        return this.timestamp;
    }

    @Override
    public InvocationContext invocationContext() {
        return this.invocationContext;
    }
}
