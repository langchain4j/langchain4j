package dev.langchain4j.audit.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.audit.api.event.AiServiceInvocationErrorEvent;

/**
 * Default implementation of {@link AiServiceInvocationErrorEvent}.
 */
public class DefaultAiServiceInvocationErrorEvent extends AbstractAiServiceInvocationEvent
        implements AiServiceInvocationErrorEvent {

    private final Throwable error;

    public DefaultAiServiceInvocationErrorEvent(AiServiceInvocationErrorEventBuilder builder) {
        super(builder);
        this.error = ensureNotNull(builder.getError(), "error");
    }

    @Override
    public Throwable error() {
        return error;
    }
}
