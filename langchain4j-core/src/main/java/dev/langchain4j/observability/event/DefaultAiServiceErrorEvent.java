package dev.langchain4j.observability.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.observability.api.event.AiServiceErrorEvent;

/**
 * Default implementation of {@link AiServiceErrorEvent}.
 */
public class DefaultAiServiceErrorEvent extends AbstractAiServiceEvent implements AiServiceErrorEvent {

    private final Throwable error;

    public DefaultAiServiceErrorEvent(AiServiceErrorEventBuilder builder) {
        super(builder);
        this.error = ensureNotNull(builder.getError(), "error");
    }

    @Override
    public Throwable error() {
        return error;
    }
}
