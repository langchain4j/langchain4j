package dev.langchain4j.observability.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.AiServiceEvent;

public abstract class AbstractAiServiceEvent implements AiServiceEvent {
    private final InvocationContext invocationContext;

    protected AbstractAiServiceEvent(Builder<?> builder) {
        ensureNotNull(builder, "builder");
        this.invocationContext = ensureNotNull(builder.invocationContext(), "invocationContext");
    }

    @Override
    public InvocationContext invocationContext() {
        return this.invocationContext;
    }
}
