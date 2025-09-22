package dev.langchain4j.audit.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.audit.api.event.AiServiceInvocationContext;
import dev.langchain4j.audit.api.event.AiServiceInvocationEvent;

public abstract class AbstractAiServiceInvocationEvent implements AiServiceInvocationEvent {
    private final AiServiceInvocationContext invocationContext;

    protected AbstractAiServiceInvocationEvent(Builder<?> builder) {
        ensureNotNull(builder, "builder");
        this.invocationContext = ensureNotNull(builder.getInvocationContext(), "invocationContext");
    }

    @Override
    public AiServiceInvocationContext invocationContext() {
        return this.invocationContext;
    }
}
