package dev.langchain4j.audit.api.listener;

import dev.langchain4j.audit.api.event.AiServiceInteractionErrorEvent;

/**
 * A listener for {@link AiServiceInteractionErrorEvent}, which represents an event
 * that occurs when an interaction with a large language model (LLM) fails.
 * This interface extends the generic {@link AiServiceInteractionEventListener},
 * specializing it for handling failure events.
 *
 * Classes implementing this interface should handle scenarios where an LLM
 * interaction encounters an error. These scenarios include capturing and
 * processing the associated error details encapsulated within the event.
 */
@FunctionalInterface
public interface LLMInteractionErrorEventListener extends AiServiceInteractionEventListener<AiServiceInteractionErrorEvent> {
    @Override
    default Class<AiServiceInteractionErrorEvent> getEventClass() {
        return AiServiceInteractionErrorEvent.class;
    }
}
