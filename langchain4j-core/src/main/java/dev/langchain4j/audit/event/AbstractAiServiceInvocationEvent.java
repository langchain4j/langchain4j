package dev.langchain4j.audit.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.audit.api.event.AiServiceInvocationEvent;
import dev.langchain4j.invocation.InvocationContext;

public abstract class AbstractAiServiceInvocationEvent implements AiServiceInvocationEvent {

    private final InvocationContext invocationContext;

    protected AbstractAiServiceInvocationEvent(Builder<?> builder) {
        ensureNotNull(builder, "builder");
        this.invocationContext = ensureNotNull(builder.invocationContext(), "invocationContext");
    }

    @Override
    public InvocationContext invocationContext() {
        return this.invocationContext;
    }
}
