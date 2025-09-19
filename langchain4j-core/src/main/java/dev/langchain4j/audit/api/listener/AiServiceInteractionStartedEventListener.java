package dev.langchain4j.audit.api.listener;

import dev.langchain4j.audit.api.event.AiServiceInteractionStartedEvent;

/**
 * A listener for {@link AiServiceInteractionStartedEvent}, which represents an event
 * that occurs when an interaction with a large language model (LLM) starts.
 * This interface extends the generic {@link AiServiceInteractionEventListener},
 * specializing it for handling events related to the initiation of an LLM interaction.
 *
 * Classes implementing this interface can respond to the event of an interaction
 * beginning, such as capturing the user or system message provided at the
 * start of the interaction.
 */
@FunctionalInterface
public interface AiServiceInteractionStartedEventListener
        extends AiServiceInteractionEventListener<AiServiceInteractionStartedEvent> {
    @Override
    default Class<AiServiceInteractionStartedEvent> getEventClass() {
        return AiServiceInteractionStartedEvent.class;
    }
}
