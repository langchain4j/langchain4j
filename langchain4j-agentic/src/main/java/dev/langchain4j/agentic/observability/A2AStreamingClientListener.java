package dev.langchain4j.agentic.observability;

/**
 * A listener invoked when an update event is received from an A2A streaming client.
 *
 * <p>The listener can inspect each event and return an
 * {@link A2AStreamingClientListenerResult} to control whether the client should
 * continue consuming the streaming response or stop consuming it and return
 * a response to the caller.
 *
 * <p>This is useful for scenarios where the caller does not need to observe the
 * entire execution of a remote A2A task. For example, the listener can stop
 * consuming the stream after receiving a specific event, allowing the client
 * invocation to return immediately while the server-side task may continue
 * executing asynchronously.
 *
 */
@FunctionalInterface
public interface A2AStreamingClientListener<E> {
    A2AStreamingClientListenerResult onUpdateEvent(E event);
}
