package dev.langchain4j.audit.api;

import dev.langchain4j.audit.api.event.LLMInteractionEvent;
import dev.langchain4j.audit.api.listener.LLMInteractionEventListener;
import dev.langchain4j.spi.audit.LLMInteractionEventListenerRegistrarFactory;
import java.util.ServiceLoader;

/**
 * A registrar for registering {@link LLMInteractionEventListener}s.
 */
public interface LLMInteractionEventListenerRegistrar {
    /**
     * Registers a listener to receive {@link LLMInteractionEvent} notifications.
     */
    <T extends LLMInteractionEvent> void register(LLMInteractionEventListener<T> listener);

    /**
     * Unregisters a previously registered {@link LLMInteractionEventListener}, stopping it from receiving further
     * {@link LLMInteractionEvent} notifications.
     */
    <T extends LLMInteractionEvent> void unregister(LLMInteractionEventListener<T> listener);

    /**
     * Fires the given event to all registered {@link LLMInteractionEventListener}s.
     *
     * @param <T>   The type of the event, which must be a subtype of {@link LLMInteractionEvent}.
     * @param event The event to be fired to the listeners. Must not be null.
     */
    <T extends LLMInteractionEvent> void fireEvent(T event);

    /**
     * Retrieves an instance of {@link LLMInteractionEventListenerRegistrar}.
     *
     * This method first attempts to load an instance of {@link LLMInteractionEventListenerRegistrar}
     * using {@link ServiceLoader}. If no implementation is found, the default
     * instance provided by {@link DefaultLLMInteractionEventListenerRegistrar#getInstance()} is returned.
     */
    static LLMInteractionEventListenerRegistrar getInstance() {
        return ServiceLoader.load(LLMInteractionEventListenerRegistrarFactory.class)
                .findFirst()
                .map(LLMInteractionEventListenerRegistrarFactory::get)
                .orElseGet(DefaultLLMInteractionEventListenerRegistrar::getInstance);
    }
}
