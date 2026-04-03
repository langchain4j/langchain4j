package dev.langchain4j.observability.api.listener;

import dev.langchain4j.observability.api.event.AiServiceStartedEvent;

/**
 * A listener for {@link AiServiceStartedEvent}, which represents an event
 * that occurs when an AI Service invocation starts.
 * This interface extends the generic {@link AiServiceListener},
 * specializing it for handling events related to the initiation of an invocation.
 *
 * Classes implementing this interface can respond to the event of an invocation
 * beginning, such as capturing the user or system message provided at the
 * start of the invocation.
 */
@FunctionalInterface
public interface AiServiceStartedListener extends AiServiceListener<AiServiceStartedEvent> {
    @Override
    default Class<AiServiceStartedEvent> getEventClass() {
        return AiServiceStartedEvent.class;
    }
}
