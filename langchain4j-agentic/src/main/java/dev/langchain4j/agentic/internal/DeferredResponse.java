package dev.langchain4j.agentic.internal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Abstract base for responses that are deferred to an external actor (e.g., a human,
 * a REST endpoint, a message queue) and must be explicitly completed via {@link #complete(Object)}.
 * <p>
 * After serialization/deserialization, a new incomplete {@link CompletableFuture} is created,
 * allowing an external system to reconnect and complete the response.
 * <p>
 * Concrete subclasses determine the runtime behavior when the response is not yet available:
 * <ul>
 *   <li>{@link PendingResponse} — blocks the calling thread until completed (future semantic)</li>
 *   <li>{@link SuspendedResponse} — suspends the agentic system by throwing
 *       {@link dev.langchain4j.agentic.scope.AgenticSystemSuspendedException} (exception semantic)</li>
 * </ul>
 *
 * @param <T> the type of the response value
 */
public abstract class DeferredResponse<T> implements DelayedResponse<T> {

    private final String responseId;

    @JsonIgnore
    private transient CompletableFuture<T> futureResponse;

    protected DeferredResponse(@JsonProperty("responseId") String responseId) {
        this.responseId = responseId;
        this.futureResponse = new CompletableFuture<>();
    }

    /**
     * Returns the unique identifier for this deferred response.
     *
     * @return the response identifier
     */
    public String responseId() {
        return responseId;
    }

    @Override
    @JsonIgnore
    public boolean isDone() {
        return futureResponse.isDone();
    }

    @Override
    @JsonIgnore
    public T blockingGet() {
        return DelayedResponse.join(futureResponse);
    }

    /**
     * Waits for the response with a timeout.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return the response value
     * @throws TimeoutException if the wait timed out
     */
    public T blockingGet(long timeout, TimeUnit unit) throws TimeoutException {
        try {
            return futureResponse.get(timeout, unit);
        } catch (TimeoutException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Completes this deferred response with the given value.
     * Any threads blocked on {@link #blockingGet()} will be released.
     *
     * @param value the response value
     * @return {@code true} if this invocation caused the response to transition to a completed state,
     *         {@code false} if it was already completed
     */
    public boolean complete(T value) {
        return futureResponse.complete(value);
    }

    /**
     * Completes this deferred response exceptionally.
     *
     * @param exception the exception
     * @return {@code true} if this invocation caused the response to transition to a completed state
     */
    public boolean completeExceptionally(Throwable exception) {
        return futureResponse.completeExceptionally(exception);
    }

    @Override
    public String toString() {
        return isDone() ? String.valueOf(result()) : "<pending:" + responseId + ">";
    }
}
