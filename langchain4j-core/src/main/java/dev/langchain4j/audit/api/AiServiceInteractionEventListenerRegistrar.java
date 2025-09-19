package dev.langchain4j.audit.api;

import dev.langchain4j.audit.api.event.AiServiceInteractionEvent;
import dev.langchain4j.audit.api.listener.AiServiceInteractionEventListener;
import dev.langchain4j.spi.audit.AiServiceInteractionEventListenerRegistrarFactory;
import java.util.ServiceLoader;

/**
 * A registrar for registering {@link AiServiceInteractionEventListener}s.
 */
public interface AiServiceInteractionEventListenerRegistrar {
    /**
     * Registers a listener to receive {@link AiServiceInteractionEvent} notifications.
     */
    <T extends AiServiceInteractionEvent> void register(AiServiceInteractionEventListener<T> listener);

    /**
     * Unregisters a previously registered {@link AiServiceInteractionEventListener}, stopping it from receiving further
     * {@link AiServiceInteractionEvent} notifications.
     */
    <T extends AiServiceInteractionEvent> void unregister(AiServiceInteractionEventListener<T> listener);

    /**
     * Fires the given event to all registered {@link AiServiceInteractionEventListener}s.
     *
     * @param <T>   The type of the event, which must be a subtype of {@link AiServiceInteractionEvent}.
     * @param event The event to be fired to the listeners. Must not be null.
     */
    <T extends AiServiceInteractionEvent> void fireEvent(T event);

    /**
     * Retrieves an instance of {@link AiServiceInteractionEventListenerRegistrar}.
     *
     * This method first attempts to load an instance of {@link AiServiceInteractionEventListenerRegistrar}
     * using {@link ServiceLoader}. If no implementation is found, the default
     * instance provided by {@link DefaultAiServiceInteractionEventListenerRegistrar#getInstance()} is returned.
     */
    static AiServiceInteractionEventListenerRegistrar getInstance() {
        return ServiceLoader.load(AiServiceInteractionEventListenerRegistrarFactory.class)
                .findFirst()
                .map(AiServiceInteractionEventListenerRegistrarFactory::get)
                .orElseGet(DefaultAiServiceInteractionEventListenerRegistrar::getInstance);
    }
}
