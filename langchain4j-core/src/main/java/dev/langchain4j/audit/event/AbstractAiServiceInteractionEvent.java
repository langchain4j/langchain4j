package dev.langchain4j.audit.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.audit.api.event.AiServiceInteractionEvent;
import dev.langchain4j.audit.api.event.InteractionSource;

public abstract class AbstractAiServiceInteractionEvent implements AiServiceInteractionEvent {
    private final InteractionSource interactionSource;

    protected AbstractAiServiceInteractionEvent(Builder<?> builder) {
        ensureNotNull(builder, "builder");
        this.interactionSource = ensureNotNull(builder.getInteractionSource(), "interactionSource");
    }

    @Override
    public InteractionSource interactionSource() {
        return this.interactionSource;
    }
}
