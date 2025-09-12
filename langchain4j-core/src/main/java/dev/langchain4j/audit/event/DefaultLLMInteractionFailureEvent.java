package dev.langchain4j.audit.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.audit.api.event.LLMInteractionFailureEvent;

/**
 * Default implementation of {@link LLMInteractionFailureEvent}.
 */
public class DefaultLLMInteractionFailureEvent extends AbstractLLMInteractionEvent
        implements LLMInteractionFailureEvent {
    private final Throwable error;

    public DefaultLLMInteractionFailureEvent(LLMInteractionFailureEventBuilder builder) {
        super(builder);
        this.error = ensureNotNull(builder.getError(), "error");
    }

    @Override
    public Throwable error() {
        return error;
    }
}
