package dev.langchain4j.observability.api.listener;

import dev.langchain4j.observability.api.event.AiServiceErrorEvent;

/**
 * A listener for {@link AiServiceErrorEvent}, which represents an event
 * that occurs when an AI Service invocation fails.
 * This interface extends the generic {@link AiServiceListener},
 * specializing it for handling failure events.
 * <p>
 * Classes implementing this interface should handle scenarios where an AI Service
 * invocation encounters an error. These scenarios include capturing and
 * processing the associated error details encapsulated within the event.
 */
@FunctionalInterface
public interface AiServiceErrorListener extends AiServiceListener<AiServiceErrorEvent> {
    @Override
    default Class<AiServiceErrorEvent> getEventClass() {
        return AiServiceErrorEvent.class;
    }
}
