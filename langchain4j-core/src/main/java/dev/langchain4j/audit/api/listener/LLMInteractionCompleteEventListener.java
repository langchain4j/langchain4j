package dev.langchain4j.audit.api.listener;

import dev.langchain4j.audit.api.event.LLMInteractionCompleteEvent;

/**
 * A listener for {@link LLMInteractionCompleteEvent}, which represents an event
 * that occurs upon the completion of an interaction with a large language model (LLM).
 * This interface extends the generic {@link LLMInteractionEventListener} interface,
 * allowing it to specifically listen for completion events.
 */
@FunctionalInterface
public interface LLMInteractionCompleteEventListener extends LLMInteractionEventListener<LLMInteractionCompleteEvent> {
    @Override
    default Class<LLMInteractionCompleteEvent> getEventClass() {
        return LLMInteractionCompleteEvent.class;
    }
}
