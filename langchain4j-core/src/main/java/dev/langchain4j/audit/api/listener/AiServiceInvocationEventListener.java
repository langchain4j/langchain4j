package dev.langchain4j.audit.api.listener;

import dev.langchain4j.audit.api.event.AiServiceInvocationEvent;

/**
 * A {@link AiServiceInvocationEvent} listener that listens for
 * @param <T> The type of {@link AiServiceInvocationEvent} this listener listens for
 */
public interface AiServiceInvocationEventListener<T extends AiServiceInvocationEvent> {
    /**
     * Retrieves the class object representing the type of {@link AiServiceInvocationEvent}
     * that this listener listens for.
     *
     * @return the {@link Class} object of the event type that this listener is associated with
     */
    Class<T> getEventClass();

    /**
     * Invoked when an event of type {@link AiServiceInvocationEvent} occurs.
     *
     * @param event The event instance that occurred, encapsulating specific information
     *              about the interaction or process related to the LLM.
     */
    void onEvent(T event);
}
