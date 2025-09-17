package dev.langchain4j.audit.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.audit.api.event.LLMInteractionErrorEvent;

/**
 * Default implementation of {@link LLMInteractionErrorEvent}.
 */
public class DefaultLLMInteractionErrorEvent extends AbstractLLMInteractionEvent implements LLMInteractionErrorEvent {
    private final Throwable error;

    public DefaultLLMInteractionErrorEvent(LLMInteractionErrorEventBuilder builder) {
        super(builder);
        this.error = ensureNotNull(builder.getError(), "error");
    }

    @Override
    public Throwable error() {
        return error;
    }
}
