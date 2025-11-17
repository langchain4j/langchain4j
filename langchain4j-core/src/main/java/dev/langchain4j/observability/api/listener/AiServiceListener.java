package dev.langchain4j.observability.api.listener;

import dev.langchain4j.observability.api.event.AiServiceEvent;

/**
 * A {@link AiServiceEvent} listener that listens for
 * @param <T> The type of {@link AiServiceEvent} this listener listens for
 */
public interface AiServiceListener<T extends AiServiceEvent> {
    /**
     * Retrieves the class object representing the type of {@link AiServiceEvent}
     * that this listener listens for.
     *
     * @return the {@link Class} object of the event type that this listener is associated with
     */
    Class<T> getEventClass();

    /**
     * Called when an event of type {@link AiServiceEvent} occurs.
     *
     * @param event The event instance that occurred, encapsulating specific information
     *              about the invocation.
     */
    void onEvent(T event);
}
