package dev.langchain4j.observability.api;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default registrar for registering {@link AiServiceListener}s.
 */
public class DefaultAiServiceListenerRegistrar implements AiServiceListenerRegistrar {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAiServiceListenerRegistrar.class);
    private final Map<Class<? extends AiServiceEvent>, EventListeners<? extends AiServiceEvent>> listeners =
            new ConcurrentHashMap<>();

    // Defaults to false to preserve backwards compatibility
    private final AtomicBoolean shouldThrowExceptionOnEventError = new AtomicBoolean(false);

    /**
     * Registers a listener to receive {@link AiServiceEvent} notifications.
     */
    @Override
    public <T extends AiServiceEvent> void register(AiServiceListener<T> listener) {
        ensureNotNull(listener, "listener");

        this.listeners.compute(
                listener.getEventClass(),
                (eventClass, eventListeners) -> addToExistingOrNewList(eventListeners, listener));
    }

    /**
     * Unregisters a previously registered {@link AiServiceListener}, stopping it from receiving further
     * {@link AiServiceEvent} notifications.
     */
    @Override
    public <T extends AiServiceEvent> void unregister(AiServiceListener<T> listener) {
        ensureNotNull(listener, "listener");
        Optional.ofNullable(this.listeners.get(listener.getEventClass()))
                .map(l -> (EventListeners<T>) l)
                .ifPresent(eventListeners -> eventListeners.remove(listener));
    }

    /**
     * Fires the given event to all registered {@link AiServiceListener}s.
     *
     * @param <T>   The type of the event, which must be a subtype of {@link AiServiceEvent}.
     * @param event The event to be fired to the listeners. Must not be null.
     */
    @Override
    public <T extends AiServiceEvent> void fireEvent(T event) {
        ensureNotNull(event, "event");
        Optional.ofNullable(this.listeners.get(event.eventClass()))
                .map(l -> (EventListeners<T>) l)
                .ifPresent(l -> l.fireEvent(event));
    }

    @Override
    public void shouldThrowExceptionOnEventError(boolean shouldThrowExceptionOnEventError) {
        this.shouldThrowExceptionOnEventError.compareAndSet(!shouldThrowExceptionOnEventError, shouldThrowExceptionOnEventError);
    }

    private <T extends AiServiceEvent> EventListeners<T> addToExistingOrNewList(
            @Nullable EventListeners<? extends AiServiceEvent> listenersList, AiServiceListener<T> listener) {

        var list = Optional.ofNullable(listenersList)
                .map(l -> (EventListeners<T>) l)
                .orElseGet(EventListeners::new);
        list.add(listener);
        return list;
    }

    private class EventListeners<T extends AiServiceEvent> {
        private final Set<@NonNull AiServiceListener<T>> listeners = new LinkedHashSet<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock(true);

        private EventListeners() {
            super();
        }

        private void add(AiServiceListener<T> listener) {
            ensureNotNull(listener, "listener");
            var writeLock = this.lock.writeLock();
            writeLock.lock();

            try {
                this.listeners.add(listener);
            } finally {
                writeLock.unlock();
            }
        }

        private void remove(AiServiceListener<T> listener) {
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

                        if (shouldThrowExceptionOnEventError.get()) {
                            throw e;
                        }
                    }
                });
            } finally {
                readLock.unlock();
            }
        }
    }
}
