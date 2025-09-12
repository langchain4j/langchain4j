package dev.langchain4j.audit.api.listener;

import dev.langchain4j.audit.api.event.LLMInteractionEvent;

/**
 * A {@link LLMInteractionEvent} listener that listens for
 * @param <T> The type of {@link LLMInteractionEvent} this listener listens for
 */
public interface LLMInteractionEventListener<T extends LLMInteractionEvent> {
    /**
     * Retrieves the class object representing the type of {@link LLMInteractionEvent}
     * that this listener listens for.
     *
     * @return the {@link Class} object of the event type that this listener is associated with
     */
    Class<T> getEventClass();

    /**
     * Invoked when an event of type {@link LLMInteractionEvent} occurs.
     *
     * @param event The event instance that occurred, encapsulating specific information
     *              about the interaction or process related to the LLM.
     */
    void onEvent(T event);
}
