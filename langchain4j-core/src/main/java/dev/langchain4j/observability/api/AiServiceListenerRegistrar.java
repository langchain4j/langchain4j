package dev.langchain4j.observability.api;

import dev.langchain4j.Internal;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceInteractionEvent;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.spi.observability.AiServiceListenerRegistrarFactory;
import java.util.Arrays;
import java.util.ServiceLoader;

/**
 * A registrar for registering {@link AiServiceListener}s.
 */
public interface AiServiceListenerRegistrar {
    /**
     * Registers a listener to receive {@link AiServiceEvent} notifications.
     */
    <T extends AiServiceEvent> void register(AiServiceListener<T> listener);

    /**
     * Registers one or more {@link AiServiceListener} instances to receive
     * {@link AiServiceEvent} notifications.
     *
     * @param listeners One or more {@link AiServiceListener} instances to register.
     *                  If null, no action will be taken.
     */
    default void register(AiServiceListener<?>... listeners) {
        if (listeners != null) {
            register(Arrays.asList(listeners));
        }
    }

    /**
     * Registers a collection of {@link AiServiceListener} instances to receive
     * {@link AiServiceEvent} notifications. If the provided collection is null, no action is taken.
     * Each listener in the collection is registered individually.
     *
     * @param listeners An {@link Iterable} containing instances of {@link AiServiceListener} to register.
     *                  Each listener will be registered to receive notifications for its associated event type.
     */
    default void register(Iterable<? extends AiServiceListener<?>> listeners) {
        if (listeners != null) {
            listeners.forEach(this::register);
        }
    }

    /**
     * Unregisters a previously registered {@link AiServiceListener}, stopping it from receiving further
     * {@link AiServiceEvent} notifications.
     */
    <T extends AiServiceEvent> void unregister(AiServiceListener<T> listener);

    /**
     * Unregisters one or more {@link AiServiceListener} instances to receive
     * {@link AiServiceEvent} notifications.
     *
     * @param listeners One or more {@link AiServiceListener} instances to unregister.
     *                  If null, no action will be taken.
     */
    default void unregister(AiServiceListener<?>... listeners) {
        if (listeners != null) {
            unregister(Arrays.asList(listeners));
        }
    }

    /**
     * Unregisters a collection of {@link AiServiceListener} instances to receive
     * {@link AiServiceEvent} notifications. If the provided collection is null, no action is taken.
     * Each listener in the collection is unregistered individually.
     *
     * @param listeners An {@link Iterable} containing instances of {@link AiServiceListener} to unregister.
     *                  Each listener will be registered to receive notifications for its associated event type.
     */
    default void unregister(Iterable<? extends AiServiceListener<?>> listeners) {
        if (listeners != null) {
            listeners.forEach(this::unregister);
        }
    }

    /**
     * Fires the given event to all registered {@link AiServiceListener}s.
     *
     * @param <T>   The type of the event, which must be a subtype of {@link AiServiceEvent}.
     * @param event The event to be fired to the listeners. Must not be null.
     */
    <T extends AiServiceEvent> void fireEvent(T event);

    /**
     * Signals that the given AI Service invocation has ended and that no further events will be fired for it.
     * <p>
     * Most invocations end by firing an {@link AiServiceCompletedEvent} or an {@link AiServiceErrorEvent}, but some
     * end without either, for example when an asynchronous or streaming invocation is canceled. AI Services call this
     * method once such an invocation is over so that implementations can release any state held for it (for example
     * the events collected for an {@link AiServiceInteractionEvent}). It may also be called for an invocation that
     * has already fired an {@link AiServiceCompletedEvent} or {@link AiServiceErrorEvent}, so implementations must
     * treat it as idempotent.
     * <p>
     * This method is called by the AI Services infrastructure and is not intended to be called by application code.
     * The default implementation does nothing.
     *
     * @param invocationContext The context of the invocation that ended. Must not be null.
     */
    @Internal
    default void invocationEnded(InvocationContext invocationContext) {}

    /**
     * Configures whether exceptions should be thrown when an error occurs during event processing.
     * If set to {@code true}, any error that occurs while processing an event will result in an exception
     * propagating to the caller. If set to {@code false}, errors will be silently handled or logged
     * without interrupting the normal execution flow.
     *
     * @param shouldThrowExceptionOnEventError Indicates whether to throw exceptions on event errors.
     *                                         If {@code true}, exceptions will be thrown; otherwise, errors
     *                                         will be handled silently. Default is {@code false}.
     */
    void shouldThrowExceptionOnEventError(boolean shouldThrowExceptionOnEventError);

    /**
     * Retrieves an instance of {@link AiServiceListenerRegistrar}.
     *
     * This method first attempts to load an instance of {@link AiServiceListenerRegistrar}
     * using {@link ServiceLoader}. If no implementation is found, the default
     * instance provided by {@link DefaultAiServiceListenerRegistrar#newInstance()} is returned.
     * <p>
     *     Simply calls {@link #newInstance(boolean)}, passing {@code false} as the argument.
     * </p>
     */
    static AiServiceListenerRegistrar newInstance() {
        return newInstance(false);
    }

    /**
     * Retrieves an instance of {@link AiServiceListenerRegistrar}.
     *
     * This method first attempts to load an instance of {@link AiServiceListenerRegistrar}
     * using {@link ServiceLoader}. If no implementation is found, the default
     * instance provided by {@link DefaultAiServiceListenerRegistrar#newInstance()} is returned.
     * @param shouldThrowExceptionOnEventError Indicates whether to throw exceptions on event errors.
     *                                         If {@code true}, exceptions will be thrown; otherwise, errors
     *                                         will be handled silently.
     */
    static AiServiceListenerRegistrar newInstance(boolean shouldThrowExceptionOnEventError) {
        var registrar = ServiceLoader.load(AiServiceListenerRegistrarFactory.class)
                .findFirst()
                .map(AiServiceListenerRegistrarFactory::get)
                .orElseGet(DefaultAiServiceListenerRegistrar::new);

        registrar.shouldThrowExceptionOnEventError(shouldThrowExceptionOnEventError);
        return registrar;
    }
}
