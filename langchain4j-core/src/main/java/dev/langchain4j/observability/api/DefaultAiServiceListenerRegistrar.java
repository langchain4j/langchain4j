package dev.langchain4j.observability.api;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceInvocationSummaryEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;
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

    // The events fired so far by each in-flight invocation that is being tracked for an AiServiceInvocationSummaryEvent,
    // keyed by invocation id. A list is only ever mutated inside an atomic ConcurrentHashMap operation on its own key,
    // so no additional locking is needed.
    private final Map<UUID, List<AiServiceEvent>> trackedInvocations = new ConcurrentHashMap<>();

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
     * <p>
     * If an {@link AiServiceInvocationSummaryEvent} listener is registered, the event is also recorded against its
     * invocation. When the invocation ends with an {@link AiServiceCompletedEvent} or an {@link AiServiceErrorEvent},
     * a single {@link AiServiceInvocationSummaryEvent} containing all of the recorded events is fired after the event itself.
     *
     * @param <T>   The type of the event, which must be a subtype of {@link AiServiceEvent}.
     * @param event The event to be fired to the listeners. Must not be null.
     * @throws IllegalArgumentException if the event is an {@link AiServiceInvocationSummaryEvent}, which is only ever fired
     *                                  by the registrar itself
     */
    @Override
    public <T extends AiServiceEvent> void fireEvent(T event) {
        ensureNotNull(event, "event");

        if (event instanceof AiServiceInvocationSummaryEvent) {
            throw new IllegalArgumentException(
                    "%s is a composite event that is fired by the registrar itself and cannot be fired directly"
                            .formatted(AiServiceInvocationSummaryEvent.class.getSimpleName()));
        }

        // Track the event before notifying listeners so a failing listener can't leave a tracked invocation behind
        var invocationSummaryEvent = trackInvocation(event);
        notifyListeners(event);
        invocationSummaryEvent.ifPresent(this::notifyListeners);
    }


    @Override
    public void shouldThrowExceptionOnEventError(boolean shouldThrowExceptionOnEventError) {
        this.shouldThrowExceptionOnEventError.compareAndSet(
                !shouldThrowExceptionOnEventError, shouldThrowExceptionOnEventError);
    }

    private <T extends AiServiceEvent> void notifyListeners(T event) {
        Optional.ofNullable(this.listeners.get(event.eventClass()))
                .map(l -> (EventListeners<T>) l)
                .ifPresent(l -> l.fireEvent(event));
    }

    private Optional<AiServiceInvocationSummaryEvent> trackInvocation(AiServiceEvent event) {
        return Optional.ofNullable(event.invocationContext())
                .map(InvocationContext::invocationId)
                .flatMap(invocationId -> isInvocationEnd(event)
                        ? endTrackedInvocation(invocationId, event.invocationContext(), Stream.of(event))
                        : recordInvocationSummaryEvent(invocationId, event));
    }

    private Optional<AiServiceInvocationSummaryEvent> recordInvocationSummaryEvent(UUID invocationId, AiServiceEvent event) {
        if (event instanceof AiServiceStartedEvent) {
            startTrackingInvocation(invocationId, event);
        } else {
            // Events for invocations that aren't tracked (or are no longer tracked) are ignored
            this.trackedInvocations.computeIfPresent(invocationId, (id, events) -> {
                events.add(event);
                return events;
            });
        }

        // Recording an event never ends an invocation
        return Optional.empty();
    }

    private void startTrackingInvocation(UUID invocationId, AiServiceEvent startedEvent) {
        // Only pay the cost of holding on to the events if someone is listening for the invocation summary
        if (hasInvocationSummaryListeners()) {
            this.trackedInvocations.putIfAbsent(invocationId, new ArrayList<>(List.of(startedEvent)));
        }
    }

    private Optional<AiServiceInvocationSummaryEvent> endTrackedInvocation(
            UUID invocationId, InvocationContext invocationContext, Stream<AiServiceEvent> finalEvents) {
        return Optional.ofNullable(this.trackedInvocations.remove(invocationId))
                .map(events -> AiServiceInvocationSummaryEvent.builder()
                        .invocationContext(invocationContext)
                        .events(Stream.concat(events.stream(), finalEvents).toList())
                        .build());
    }

    private boolean hasInvocationSummaryListeners() {
        return Optional.ofNullable(this.listeners.get(AiServiceInvocationSummaryEvent.class))
                .map(eventListeners -> !eventListeners.isEmpty())
                .orElse(false);
    }

    private static boolean isInvocationEnd(AiServiceEvent event) {
        return (event instanceof AiServiceCompletedEvent)
                || (event instanceof AiServiceErrorEvent)
                || (event instanceof AiServiceCancelledEvent);
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

        private boolean isEmpty() {
            var readLock = this.lock.readLock();
            readLock.lock();

            try {
                return this.listeners.isEmpty();
            } finally {
                readLock.unlock();
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
