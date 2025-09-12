package dev.langchain4j.audit.api;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.audit.api.event.LLMInteractionEvent;
import dev.langchain4j.audit.api.listener.LLMInteractionEventListener;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A default registrar for registering {@link LLMInteractionEventListener}s.
 */
public class DefaultLLMInteractionEventListenerRegistrar implements LLMInteractionEventListenerRegistrar {
    private static final LLMInteractionEventListenerRegistrar INSTANCE =
            new DefaultLLMInteractionEventListenerRegistrar();

    private final Map<Class<? extends LLMInteractionEvent>, EventListeners<? extends LLMInteractionEvent>> listeners =
            new ConcurrentHashMap<>();

    private DefaultLLMInteractionEventListenerRegistrar() {
        super();
    }

    /**
     * Provides the instance of {@link LLMInteractionEventListenerRegistrar}.
     */
    public static LLMInteractionEventListenerRegistrar getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a listener to receive {@link LLMInteractionEvent} notifications.
     */
    @Override
    public <T extends LLMInteractionEvent> void register(LLMInteractionEventListener<T> listener) {
        ensureNotNull(listener, "listener");

        this.listeners.compute(
                listener.getEventClass(),
                (eventClass, eventListeners) -> addToExistingOrNewList(eventListeners, listener));
    }

    /**
     * Unregisters a previously registered {@link LLMInteractionEventListener}, stopping it from receiving further
     * {@link LLMInteractionEvent} notifications.
     */
    @Override
    public <T extends LLMInteractionEvent> void unregister(LLMInteractionEventListener<T> listener) {
        ensureNotNull(listener, "listener");
        Optional.ofNullable(this.listeners.get(listener.getEventClass()))
                .map(l -> (EventListeners<T>) l)
                .ifPresent(eventListeners -> eventListeners.remove(listener));
    }

    /**
     * Fires the given event to all registered {@link LLMInteractionEventListener}s.
     *
     * @param <T>   The type of the event, which must be a subtype of {@link LLMInteractionEvent}.
     * @param event The event to be fired to the listeners. Must not be null.
     */
    @Override
    public <T extends LLMInteractionEvent> void fireEvent(T event) {
        ensureNotNull(event, "event");
        Optional.ofNullable(this.listeners.get(event.eventClass()))
                .map(l -> (EventListeners<T>) l)
                .ifPresent(l -> l.fireEvent(event));
    }

    private <T extends LLMInteractionEvent> EventListeners<T> addToExistingOrNewList(
            @Nullable EventListeners<? extends LLMInteractionEvent> listenersList,
            LLMInteractionEventListener<T> listener) {

        var list = Optional.ofNullable(listenersList)
                .map(l -> (EventListeners<T>) l)
                .orElseGet(EventListeners::new);
        list.add(listener);
        return list;
    }

    private static class EventListeners<T extends LLMInteractionEvent> {
        /**
         * <strong>Implementation note:</strong> I chose {@link CopyOnWriteArraySet} here because it is thread-safe.
         * The list will be very read heavy. The only time writes will occur is when listeners are initially registered and subsequently unregistered.
         * I felt this was better than alternatives involving coarse-grained locking/synchronization
         * (i.e. {@link java.util.Collections#synchronizedSet(Set)} or {@link java.util.concurrent.locks.ReadWriteLock}).
         */
        private final Set<@NonNull LLMInteractionEventListener<T>> listeners = new CopyOnWriteArraySet<>();

        private EventListeners() {
            super();
        }

        private void add(LLMInteractionEventListener<T> listener) {
            this.listeners.add(ensureNotNull(listener, "listener"));
        }

        private void remove(LLMInteractionEventListener<T> listener) {
            this.listeners.remove(ensureNotNull(listener, "listener"));
        }

        private void fireEvent(T event) {
            ensureNotNull(event, "event");
            this.listeners.forEach(listener -> listener.onEvent(event));
        }
    }
}
