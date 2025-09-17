package dev.langchain4j.audit.api.listener;

import dev.langchain4j.audit.api.event.AiServiceInvocationCompletedEvent;

/**
 * A listener for {@link AiServiceInvocationCompletedEvent}, which represents an event
 * that occurs upon the completion of an interaction with a large language model (LLM).
 * This interface extends the generic {@link AiServiceInteractionEventListener} interface,
 * allowing it to specifically listen for completion events.
 */
@FunctionalInterface
public interface AiServiceInteractionCompletedEventListener extends AiServiceInteractionEventListener<AiServiceInvocationCompletedEvent> {

    @Override
    default Class<AiServiceInvocationCompletedEvent> getEventClass() {
        return AiServiceInvocationCompletedEvent.class;
    }
}
