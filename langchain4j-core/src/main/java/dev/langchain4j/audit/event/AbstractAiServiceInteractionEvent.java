package dev.langchain4j.audit.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.audit.api.event.AiServiceInteractionEvent;
import dev.langchain4j.audit.api.event.AiServiceInvocationContext;

public abstract class AbstractAiServiceInteractionEvent implements AiServiceInteractionEvent {
    private final AiServiceInvocationContext invocationContext;

    protected AbstractAiServiceInteractionEvent(Builder<?> builder) {
        ensureNotNull(builder, "builder");
        this.invocationContext = ensureNotNull(builder.getInvocationContext(), "invocationContext");
    }

    @Override
    public AiServiceInvocationContext invocationContext() {
        return this.invocationContext;
    }
}
