package dev.langchain4j.audit.api;

import java.util.ServiceLoader;
import dev.langchain4j.audit.api.event.AiServiceInvocationEvent;
import dev.langchain4j.audit.api.listener.AiServiceInvocationEventListener;
import dev.langchain4j.spi.audit.AiServiceInteractionEventListenerRegistrarFactory;

/**
 * A registrar for registering {@link AiServiceInvocationEventListener}s.
 */
public interface AiServiceInvocationEventListenerRegistrar {
    /**
     * Registers a listener to receive {@link AiServiceInvocationEvent} notifications.
     */
    <T extends AiServiceInvocationEvent> void register(AiServiceInvocationEventListener<T> listener);

    /**
     * Unregisters a previously registered {@link AiServiceInvocationEventListener}, stopping it from receiving further
     * {@link AiServiceInvocationEvent} notifications.
     */
    <T extends AiServiceInvocationEvent> void unregister(AiServiceInvocationEventListener<T> listener);

    /**
     * Fires the given event to all registered {@link AiServiceInvocationEventListener}s.
     *
     * @param <T>   The type of the event, which must be a subtype of {@link AiServiceInvocationEvent}.
     * @param event The event to be fired to the listeners. Must not be null.
     */
    <T extends AiServiceInvocationEvent> void fireEvent(T event);

    /**
     * Retrieves an instance of {@link AiServiceInvocationEventListenerRegistrar}.
     *
     * This method first attempts to load an instance of {@link AiServiceInvocationEventListenerRegistrar}
     * using {@link ServiceLoader}. If no implementation is found, the default
     * instance provided by {@link DefaultAiServiceInvocationEventListenerRegistrar#getInstance()} is returned.
     */
    static AiServiceInvocationEventListenerRegistrar getInstance() {
        return ServiceLoader.load(AiServiceInteractionEventListenerRegistrarFactory.class)
                .findFirst()
                .map(AiServiceInteractionEventListenerRegistrarFactory::get)
                .orElseGet(DefaultAiServiceInvocationEventListenerRegistrar::getInstance);
    }
}
