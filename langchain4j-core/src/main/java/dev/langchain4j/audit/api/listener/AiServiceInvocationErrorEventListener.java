package dev.langchain4j.audit.api.listener;

import dev.langchain4j.audit.api.event.AiServiceInvocationErrorEvent;

/**
 * A listener for {@link AiServiceInvocationErrorEvent}, which represents an event
 * that occurs when an interaction with a large language model (LLM) fails.
 * This interface extends the generic {@link AiServiceInvocationEventListener},
 * specializing it for handling failure events.
 *
 * Classes implementing this interface should handle scenarios where an LLM
 * interaction encounters an error. These scenarios include capturing and
 * processing the associated error details encapsulated within the event.
 */
@FunctionalInterface
public interface AiServiceInvocationErrorEventListener
        extends AiServiceInvocationEventListener<AiServiceInvocationErrorEvent> {
    @Override
    default Class<AiServiceInvocationErrorEvent> getEventClass() {
        return AiServiceInvocationErrorEvent.class;
    }
}
