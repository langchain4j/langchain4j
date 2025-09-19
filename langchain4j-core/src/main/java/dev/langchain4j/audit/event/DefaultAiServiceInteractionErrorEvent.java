package dev.langchain4j.audit.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.audit.api.event.AiServiceInteractionErrorEvent;

/**
 * Default implementation of {@link AiServiceInteractionErrorEvent}.
 */
public class DefaultAiServiceInteractionErrorEvent extends AbstractAiServiceInteractionEvent
        implements AiServiceInteractionErrorEvent {
    private final Throwable error;

    public DefaultAiServiceInteractionErrorEvent(AiServiceInteractionErrorEventBuilder builder) {
        super(builder);
        this.error = ensureNotNull(builder.getError(), "error");
    }

    @Override
    public Throwable error() {
        return error;
    }
}
