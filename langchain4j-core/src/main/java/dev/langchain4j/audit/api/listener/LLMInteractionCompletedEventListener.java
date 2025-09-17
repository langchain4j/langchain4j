package dev.langchain4j.audit.api.listener;

import dev.langchain4j.audit.api.event.LLMInteractionCompletedEvent;

/**
 * A listener for {@link LLMInteractionCompletedEvent}, which represents an event
 * that occurs upon the completion of an interaction with a large language model (LLM).
 * This interface extends the generic {@link LLMInteractionEventListener} interface,
 * allowing it to specifically listen for completion events.
 */
@FunctionalInterface
public interface LLMInteractionCompletedEventListener
        extends LLMInteractionEventListener<LLMInteractionCompletedEvent> {
    @Override
    default Class<LLMInteractionCompletedEvent> getEventClass() {
        return LLMInteractionCompletedEvent.class;
    }
}
