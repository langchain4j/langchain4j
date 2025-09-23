package dev.langchain4j.audit.api;

import dev.langchain4j.audit.api.event.AiServiceInvocationEvent;
import dev.langchain4j.audit.api.listener.AiServiceInvocationEventListener;
import dev.langchain4j.spi.audit.AiServiceInteractionEventListenerRegistrarFactory;
import java.util.Arrays;
import java.util.ServiceLoader;

/**
 * A registrar for registering {@link AiServiceInvocationEventListener}s.
 */
public interface AiServiceInvocationEventListenerRegistrar {
    /**
     * Registers a listener to receive {@link AiServiceInvocationEvent} notifications.
     */
    <T extends AiServiceInvocationEvent> void register(AiServiceInvocationEventListener<T> listener);

    /**
     * Registers one or more {@link AiServiceInvocationEventListener} instances to receive
     * {@link AiServiceInvocationEvent} notifications.
     *
     * @param listeners One or more {@link AiServiceInvocationEventListener} instances to register.
     *                  If null, no action will be taken.
     */
    default void register(AiServiceInvocationEventListener<?>... listeners) {
        if (listeners != null) {
            register(Arrays.asList(listeners));
        }
    }

    /**
     * Registers a collection of {@link AiServiceInvocationEventListener} instances to receive
     * {@link AiServiceInvocationEvent} notifications. If the provided collection is null, no action is taken.
     * Each listener in the collection is registered individually.
     *
     * @param listeners An {@link Iterable} containing instances of {@link AiServiceInvocationEventListener} to register.
     *                  Each listener will be registered to receive notifications for its associated event type.
     */
    default void register(Iterable<? extends AiServiceInvocationEventListener<?>> listeners) {
        if (listeners != null) {
            listeners.forEach(this::register);
        }
    }

    /**
     * Unregisters a previously registered {@link AiServiceInvocationEventListener}, stopping it from receiving further
     * {@link AiServiceInvocationEvent} notifications.
     */
    <T extends AiServiceInvocationEvent> void unregister(AiServiceInvocationEventListener<T> listener);

    /**
     * Unregisters one or more {@link AiServiceInvocationEventListener} instances to receive
     * {@link AiServiceInvocationEvent} notifications.
     *
     * @param listeners One or more {@link AiServiceInvocationEventListener} instances to unregister.
     *                  If null, no action will be taken.
     */
    default void unregister(AiServiceInvocationEventListener<?>... listeners) {
        if (listeners != null) {
            unregister(Arrays.asList(listeners));
        }
    }

    /**
     * Unregisters a collection of {@link AiServiceInvocationEventListener} instances to receive
     * {@link AiServiceInvocationEvent} notifications. If the provided collection is null, no action is taken.
     * Each listener in the collection is unregistered individually.
     *
     * @param listeners An {@link Iterable} containing instances of {@link AiServiceInvocationEventListener} to unregister.
     *                  Each listener will be registered to receive notifications for its associated event type.
     */
    default void unregister(Iterable<? extends AiServiceInvocationEventListener<?>> listeners) {
        if (listeners != null) {
            listeners.forEach(this::unregister);
        }
    }

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
     * instance provided by {@link DefaultAiServiceInvocationEventListenerRegistrar#newInstance()} is returned.
     */
    static AiServiceInvocationEventListenerRegistrar newInstance() {
        return ServiceLoader.load(AiServiceInteractionEventListenerRegistrarFactory.class)
                .findFirst()
                .map(AiServiceInteractionEventListenerRegistrarFactory::get)
                .orElseGet(DefaultAiServiceInvocationEventListenerRegistrar::new);
    }
}
