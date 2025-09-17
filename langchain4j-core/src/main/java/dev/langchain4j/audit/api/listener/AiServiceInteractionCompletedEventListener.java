package dev.langchain4j.audit.api.listener;

import dev.langchain4j.audit.api.event.AiServiceInteractionCompletedEvent;

/**
 * A listener for {@link AiServiceInteractionCompletedEvent}, which represents an event
 * that occurs upon the completion of an interaction with a large language model (LLM).
 * This interface extends the generic {@link AiServiceInteractionEventListener} interface,
 * allowing it to specifically listen for completion events.
 */
@FunctionalInterface
public interface AiServiceInteractionCompletedEventListener
        extends AiServiceInteractionEventListener<AiServiceInteractionCompletedEvent> {

    @Override
    default Class<AiServiceInteractionCompletedEvent> getEventClass() {
        return AiServiceInteractionCompletedEvent.class;
    }
}
