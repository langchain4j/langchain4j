package dev.langchain4j.agentic.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A {@link DelayedResponse} that can be completed externally, without spawning a background thread.
 * <p>
 * Unlike {@link AsyncResponse}, which immediately starts executing a supplier on a thread pool,
 * {@code PendingResponse} creates an initially incomplete future that must be explicitly completed
 * via {@link #complete(Object)}. This makes it suitable for scenarios where the response comes from
 * an external source (e.g., a human via a REST API, a message queue, or an external event) and the
 * workflow must survive process restarts.
 * <p>
 * After serialization/deserialization, a new incomplete {@link CompletableFuture} is created,
 * allowing an external system to reconnect and complete the response.
 * <p>
 * Usage with {@link dev.langchain4j.agentic.workflow.HumanInTheLoop}:
 * <pre>{@code
 * HumanInTheLoop.builder()
 *     .responseProvider(scope -> new PendingResponse<>("user-approval"))
 *     .build();
 *
 * // Later, from an external system (e.g., REST endpoint):
 * scope.completePendingResponse("user-approval", "approved");
 * }</pre>
 *
 * @param <T> the type of the response value
 */
public class PendingResponse<T> implements DelayedResponse<T> {

    private final String responseId;

    @JsonIgnore
    private transient CompletableFuture<T> futureResponse;

    /**
     * Creates a new pending response with the given unique identifier.
     *
     * @param responseId a unique identifier for this pending response, used to locate and
     *                   complete it from external systems
     */
    @JsonCreator
    public PendingResponse(@JsonProperty("responseId") String responseId) {
        this.responseId = responseId;
        this.futureResponse = new CompletableFuture<>();
    }

    /**
     * Returns the unique identifier for this pending response.
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
        return futureResponse.join();
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
     * Completes this pending response with the given value.
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
     * Completes this pending response exceptionally.
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
