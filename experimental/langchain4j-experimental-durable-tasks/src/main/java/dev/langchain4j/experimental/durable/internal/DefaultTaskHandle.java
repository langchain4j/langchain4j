package dev.langchain4j.experimental.durable.internal;

import dev.langchain4j.Experimental;
import dev.langchain4j.experimental.durable.task.TaskHandle;
import dev.langchain4j.experimental.durable.task.TaskId;
import dev.langchain4j.experimental.durable.task.TaskMetadata;
import dev.langchain4j.experimental.durable.task.TaskStatus;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Default implementation of {@link TaskHandle} backed by a {@link CompletableFuture}
 * and task metadata.
 */
@Experimental
public class DefaultTaskHandle implements TaskHandle {

    private final TaskId taskId;
    private final CompletableFuture<Object> future;
    private volatile TaskMetadata metadata;
    private volatile Function<TaskId, Boolean> cancelCallback;

    public DefaultTaskHandle(TaskId taskId, TaskMetadata metadata, CompletableFuture<Object> future) {
        this.taskId = taskId;
        this.metadata = metadata;
        this.future = future;
    }

    @Override
    public TaskId id() {
        return taskId;
    }

    @Override
    public TaskStatus status() {
        return metadata.status();
    }

    @Override
    public Optional<Object> result() {
        if (future.isDone() && !future.isCancelled() && !future.isCompletedExceptionally()) {
            return Optional.ofNullable(future.join());
        }
        return Optional.empty();
    }

    @Override
    public CompletableFuture<Object> awaitResult() {
        return future;
    }

    @Override
    public boolean cancel() {
        if (cancelCallback != null) {
            return cancelCallback.apply(taskId);
        }
        // Fallback: only cancel the future (should not happen when wired through service)
        return future.cancel(true);
    }

    /**
     * Sets the cancel callback that delegates to the service-level cancel.
     *
     * @param cancelCallback function that performs store-atomic cancellation
     */
    public void setCancelCallback(Function<TaskId, Boolean> cancelCallback) {
        this.cancelCallback = cancelCallback;
    }

    /**
     * Updates the metadata snapshot held by this handle.
     *
     * @param metadata the updated metadata
     */
    public void updateMetadata(TaskMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Returns the underlying future.
     *
     * @return the completable future
     */
    public CompletableFuture<Object> future() {
        return future;
    }
}
