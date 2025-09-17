package dev.langchain4j.audit.api;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import dev.langchain4j.audit.api.event.AiServiceInteractionEvent;
import dev.langchain4j.audit.api.listener.AiServiceInteractionEventListener;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A default registrar for registering {@link AiServiceInteractionEventListener}s.
 */
public class DefaultAiServiceInteractionEventListenerRegistrar implements AiServiceInteractionEventListenerRegistrar {
    private static final AiServiceInteractionEventListenerRegistrar INSTANCE =
            new DefaultAiServiceInteractionEventListenerRegistrar();

    private final Map<Class<? extends AiServiceInteractionEvent>, EventListeners<? extends AiServiceInteractionEvent>> listeners =
            new ConcurrentHashMap<>();

    private DefaultAiServiceInteractionEventListenerRegistrar() {
        super();
    }

    /**
     * Provides the instance of {@link AiServiceInteractionEventListenerRegistrar}.
     */
    public static AiServiceInteractionEventListenerRegistrar getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a listener to receive {@link AiServiceInteractionEvent} notifications.
     */
    @Override
    public <T extends AiServiceInteractionEvent> void register(AiServiceInteractionEventListener<T> listener) {
        ensureNotNull(listener, "listener");

        this.listeners.compute(
                listener.getEventClass(),
                (eventClass, eventListeners) -> addToExistingOrNewList(eventListeners, listener));
    }

    /**
     * Unregisters a previously registered {@link AiServiceInteractionEventListener}, stopping it from receiving further
     * {@link AiServiceInteractionEvent} notifications.
     */
    @Override
    public <T extends AiServiceInteractionEvent> void unregister(AiServiceInteractionEventListener<T> listener) {
        ensureNotNull(listener, "listener");
        Optional.ofNullable(this.listeners.get(listener.getEventClass()))
                .map(l -> (EventListeners<T>) l)
                .ifPresent(eventListeners -> eventListeners.remove(listener));
    }

    /**
     * Fires the given event to all registered {@link AiServiceInteractionEventListener}s.
     *
     * @param <T>   The type of the event, which must be a subtype of {@link AiServiceInteractionEvent}.
     * @param event The event to be fired to the listeners. Must not be null.
     */
    @Override
    public <T extends AiServiceInteractionEvent> void fireEvent(T event) {
        ensureNotNull(event, "event");
        Optional.ofNullable(this.listeners.get(event.eventClass()))
                .map(l -> (EventListeners<T>) l)
                .ifPresent(l -> l.fireEvent(event));
    }

    private <T extends AiServiceInteractionEvent> EventListeners<T> addToExistingOrNewList(
            @Nullable EventListeners<? extends AiServiceInteractionEvent> listenersList,
            AiServiceInteractionEventListener<T> listener) {

        var list = Optional.ofNullable(listenersList)
                .map(l -> (EventListeners<T>) l)
                .orElseGet(EventListeners::new);
        list.add(listener);
        return list;
    }

    private static class EventListeners<T extends AiServiceInteractionEvent> {
        /**
         * <strong>Implementation note:</strong> I chose {@link CopyOnWriteArraySet} here because it is thread-safe.
         * The list will be very read heavy. The only time writes will occur is when listeners are initially registered and subsequently unregistered.
         * I felt this was better than alternatives involving coarse-grained locking/synchronization
         * (i.e. {@link java.util.Collections#synchronizedSet(Set)} or {@link java.util.concurrent.locks.ReadWriteLock}).
         */
        private final Set<@NonNull AiServiceInteractionEventListener<T>> listeners = new CopyOnWriteArraySet<>();

        private EventListeners() {
            super();
        }

        private void add(AiServiceInteractionEventListener<T> listener) {
            this.listeners.add(ensureNotNull(listener, "listener"));
        }

        private void remove(AiServiceInteractionEventListener<T> listener) {
            this.listeners.remove(ensureNotNull(listener, "listener"));
        }

        private void fireEvent(T event) {
            ensureNotNull(event, "event");
            this.listeners.forEach(listener -> listener.onEvent(event));
        }
    }
}
