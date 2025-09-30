package dev.langchain4j.observability.api.listener;

import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;

/**
 * A listener for {@link AiServiceCompletedEvent}, which represents an event
 * that occurs upon the completion of an AI Service invocation.
 * This interface extends the generic {@link AiServiceListener} interface,
 * allowing it to specifically listen for completion events.
 */
@FunctionalInterface
public interface AiServiceCompletedListener extends AiServiceListener<AiServiceCompletedEvent> {

    @Override
    default Class<AiServiceCompletedEvent> getEventClass() {
        return AiServiceCompletedEvent.class;
    }
}
