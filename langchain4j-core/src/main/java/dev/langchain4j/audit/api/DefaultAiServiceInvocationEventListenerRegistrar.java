package dev.langchain4j.audit.api;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.audit.api.event.AiServiceInvocationEvent;
import dev.langchain4j.audit.api.listener.AiServiceInvocationEventListener;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default registrar for registering {@link AiServiceInvocationEventListener}s.
 */
public class DefaultAiServiceInvocationEventListenerRegistrar implements AiServiceInvocationEventListenerRegistrar {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAiServiceInvocationEventListenerRegistrar.class);

    private static final AiServiceInvocationEventListenerRegistrar INSTANCE =
            new DefaultAiServiceInvocationEventListenerRegistrar();

    private final Map<Class<? extends AiServiceInvocationEvent>, EventListeners<? extends AiServiceInvocationEvent>>
            listeners = new ConcurrentHashMap<>();

    private DefaultAiServiceInvocationEventListenerRegistrar() {
        super();
    }

    /**
     * Provides the instance of {@link AiServiceInvocationEventListenerRegistrar}.
     */
    public static AiServiceInvocationEventListenerRegistrar getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a listener to receive {@link AiServiceInvocationEvent} notifications.
     */
    @Override
    public <T extends AiServiceInvocationEvent> void register(AiServiceInvocationEventListener<T> listener) {
        ensureNotNull(listener, "listener");

        this.listeners.compute(
                listener.getEventClass(),
                (eventClass, eventListeners) -> addToExistingOrNewList(eventListeners, listener));
    }

    /**
     * Unregisters a previously registered {@link AiServiceInvocationEventListener}, stopping it from receiving further
     * {@link AiServiceInvocationEvent} notifications.
     */
    @Override
    public <T extends AiServiceInvocationEvent> void unregister(AiServiceInvocationEventListener<T> listener) {
        ensureNotNull(listener, "listener");
        Optional.ofNullable(this.listeners.get(listener.getEventClass()))
                .map(l -> (EventListeners<T>) l)
                .ifPresent(eventListeners -> eventListeners.remove(listener));
    }

    /**
     * Fires the given event to all registered {@link AiServiceInvocationEventListener}s.
     *
     * @param <T>   The type of the event, which must be a subtype of {@link AiServiceInvocationEvent}.
     * @param event The event to be fired to the listeners. Must not be null.
     */
    @Override
    public <T extends AiServiceInvocationEvent> void fireEvent(T event) {
        ensureNotNull(event, "event");
        Optional.ofNullable(this.listeners.get(event.eventClass()))
                .map(l -> (EventListeners<T>) l)
                .ifPresent(l -> l.fireEvent(event));
    }

    private <T extends AiServiceInvocationEvent> EventListeners<T> addToExistingOrNewList(
            @Nullable EventListeners<? extends AiServiceInvocationEvent> listenersList,
            AiServiceInvocationEventListener<T> listener) {

        var list = Optional.ofNullable(listenersList)
                .map(l -> (EventListeners<T>) l)
                .orElseGet(EventListeners::new);
        list.add(listener);
        return list;
    }

    private static class EventListeners<T extends AiServiceInvocationEvent> {
        private final Set<@NonNull AiServiceInvocationEventListener<T>> listeners = new LinkedHashSet<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock(true);

        private EventListeners() {
            super();
        }

        private void add(AiServiceInvocationEventListener<T> listener) {
            ensureNotNull(listener, "listener");
            var writeLock = this.lock.writeLock();
            writeLock.lock();

            try {
                this.listeners.add(listener);
            } finally {
                writeLock.unlock();
            }
        }

        private void remove(AiServiceInvocationEventListener<T> listener) {
            var writeLock = this.lock.writeLock();
            writeLock.lock();

            try {
                this.listeners.remove(ensureNotNull(listener, "listener"));
            } finally {
                writeLock.unlock();
            }
        }

        private void fireEvent(T event) {
            ensureNotNull(event, "event");
            var readLock = this.lock.readLock();
            readLock.lock();

            try {
                this.listeners.forEach(listener -> {
                    try {
                        listener.onEvent(event);
                    } catch (Exception e) {
                        LOG.warn(
                                "An error occurred while firing event (%s) to listener (%s): %s"
                                        .formatted(
                                                event.getClass().getName(),
                                                listener.getClass().getName(),
                                                e.getMessage()),
                                e);
                    }
                });
            } finally {
                readLock.unlock();
            }
        }
    }
}
