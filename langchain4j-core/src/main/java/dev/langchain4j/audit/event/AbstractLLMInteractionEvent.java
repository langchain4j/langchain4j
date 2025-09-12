package dev.langchain4j.audit.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.audit.api.event.InteractionSource;
import dev.langchain4j.audit.api.event.LLMInteractionEvent;

public abstract class AbstractLLMInteractionEvent implements LLMInteractionEvent {
    private final InteractionSource interactionSource;

    protected AbstractLLMInteractionEvent(Builder<?> builder) {
        ensureNotNull(builder, "builder");
        this.interactionSource = ensureNotNull(builder.getInteractionSource(), "interactionSource");
    }

    @Override
    public InteractionSource interactionSource() {
        return this.interactionSource;
    }
}
