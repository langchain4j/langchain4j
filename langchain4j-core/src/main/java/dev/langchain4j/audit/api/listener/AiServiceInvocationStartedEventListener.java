package dev.langchain4j.audit.api.listener;

import dev.langchain4j.audit.api.event.AiServiceInvocationStartedEvent;

/**
 * A listener for {@link AiServiceInvocationStartedEvent}, which represents an event
 * that occurs when an interaction with a large language model (LLM) starts.
 * This interface extends the generic {@link AiServiceInvocationEventListener},
 * specializing it for handling events related to the initiation of an LLM interaction.
 *
 * Classes implementing this interface can respond to the event of an interaction
 * beginning, such as capturing the user or system message provided at the
 * start of the interaction.
 */
@FunctionalInterface
public interface AiServiceInvocationStartedEventListener
        extends AiServiceInvocationEventListener<AiServiceInvocationStartedEvent> {
    @Override
    default Class<AiServiceInvocationStartedEvent> getEventClass() {
        return AiServiceInvocationStartedEvent.class;
    }
}
