package dev.langchain4j.audit.api.listener;

import dev.langchain4j.audit.api.event.AiServiceInvocationStartedEvent;

/**
 * A listener for {@link AiServiceInvocationStartedEvent}, which represents an event
 * that occurs when an AI Service invocation starts.
 * This interface extends the generic {@link AiServiceInvocationEventListener},
 * specializing it for handling events related to the initiation of an invocation.
 *
 * Classes implementing this interface can respond to the event of an invocation
 * beginning, such as capturing the user or system message provided at the
 * start of the invocation.
 */
@FunctionalInterface
public interface AiServiceInvocationStartedEventListener
        extends AiServiceInvocationEventListener<AiServiceInvocationStartedEvent> {
    @Override
    default Class<AiServiceInvocationStartedEvent> getEventClass() {
        return AiServiceInvocationStartedEvent.class;
    }
}
