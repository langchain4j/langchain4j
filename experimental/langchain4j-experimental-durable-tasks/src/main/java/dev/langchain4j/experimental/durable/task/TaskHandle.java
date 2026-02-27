package dev.langchain4j.experimental.durable.task;

import dev.langchain4j.Experimental;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A handle to a running or completed durable long-lived task.
 *
 * <p>Returned by {@link dev.langchain4j.experimental.durable.LongLivedTaskService#start LongLivedTaskService.start()}
 * and {@link dev.langchain4j.experimental.durable.LongLivedTaskService#resume LongLivedTaskService.resume()}.
 * Provides access to the task's identity, current status, result, and cancellation.
 */
@Experimental
public interface TaskHandle {

    /**
     * Returns the unique identifier of this task.
     *
     * @return the task id
     */
    TaskId id();

    /**
     * Returns the current status of this task. The status is kept in sync with the
     * execution lifecycle via the service and reflects the latest known state.
     *
     * @return the current status
     */
    TaskStatus status();

    /**
     * Returns the task result if the task has completed successfully.
     *
     * @return an Optional containing the result, or empty if not yet completed
     */
    Optional<Object> result();

    /**
     * Returns a {@link CompletableFuture} that completes when the task finishes
     * (successfully, with failure, or with cancellation).
     *
     * @return a future that resolves to the task result
     */
    CompletableFuture<Object> awaitResult();

    /**
     * Cancels this task. If the task is currently running, an interrupt is sent
     * to the executing thread. The task transitions to {@link TaskStatus#CANCELLED}.
     *
     * @return {@code true} if the task was cancelled, {@code false} if it was already in a terminal state
     */
    boolean cancel();
}
