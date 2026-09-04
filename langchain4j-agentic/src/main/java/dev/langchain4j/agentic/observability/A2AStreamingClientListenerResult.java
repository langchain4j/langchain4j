package dev.langchain4j.agentic.observability;

/**
 * Represents the outcome returned by an A2A (agent-to-agent) streaming client listener.
 *
 * <p>The result allows the listener to control whether the client should continue
 * consuming the streaming response and, when stopping, optionally provide a final
 * response to the caller.
 *
 * <p>This is particularly useful when the caller is only interested in certain
 * events or does not need to observe the entire execution. In such cases, the
 * listener can request the client to stop consuming the stream and return a
 * response immediately, while the A2A server-side task may continue executing
 * asynchronously.
 *
 * <p>Consumers should use the provided factory methods {@link #continueStreaming()}
 * and {@link #stopWithResponse(String)} to clearly express the desired behavior.
 *
 * <p>Typical usage:
 * <pre>

 * // Continue consuming the streaming response
 * return A2AStreamingClientListenerResult.continueStreaming();
 *
 * // Stop consuming the stream and return immediately
 * // while the server-side task may continue asynchronously
 * return A2AStreamingClientListenerResult.stopWithResponse("Task accepted");
 * </pre>
 */

public record A2AStreamingClientListenerResult(
        boolean stop,
        String response
) {
    /**
     * Creates a result that signals the streaming process should continue.
     *
     * @return an instance with {@code stop=false} and {@code response=null}
     */
    public static A2AStreamingClientListenerResult continueStreaming() {
        return new A2AStreamingClientListenerResult(false, null);
    }

    /**
     * Creates a result that signals the streaming process should stop and provides
     * an optional final response to return to the caller.
     *
     * @param response the final response to use when stopping; may be {@code null} to
     *                 indicate stopping without providing a message
     * @return an instance with {@code stop=true} and the provided {@code response}
     */
    public static A2AStreamingClientListenerResult stopWithResponse(String response) {
        return new A2AStreamingClientListenerResult(true, response);
    }
}
