package dev.langchain4j.experimental.durable.store;

import dev.langchain4j.Experimental;
import dev.langchain4j.experimental.durable.store.event.TaskEvent;
import dev.langchain4j.experimental.durable.task.TaskId;
import dev.langchain4j.experimental.durable.task.TaskMetadata;
import dev.langchain4j.experimental.durable.task.TaskStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service provider interface for durable task persistence.
 *
 * <p>Implementations must be safe for concurrent access from multiple threads.
 * All mutating operations should be atomic with respect to a single task.
 *
 * <p>Two built-in implementations are provided:
 * <ul>
 *   <li>{@code InMemoryTaskExecutionStore} — for testing and development</li>
 *   <li>{@code FileTaskExecutionStore} — for single-node production use</li>
 * </ul>
 */
@Experimental
public interface TaskExecutionStore {

    /**
     * Saves or updates the metadata for a task.
     *
     * @param metadata the task metadata to persist
     * @throws TaskStoreException if the operation fails
     */
    void saveMetadata(TaskMetadata metadata);

    /**
     * Loads the metadata for a task.
     *
     * @param taskId the task identifier
     * @return the metadata, or empty if no task with that id is known
     * @throws TaskStoreException if the operation fails
     */
    Optional<TaskMetadata> loadMetadata(TaskId taskId);

    /**
     * Appends an event to a task's execution journal.
     *
     * <p>Events must be appended in order and are never modified or removed.
     *
     * @param event the event to append
     * @throws TaskStoreException if the operation fails
     */
    void appendEvent(TaskEvent event);

    /**
     * Returns all events for a task, in the order they were appended.
     *
     * @param taskId the task identifier
     * @return ordered list of events, empty if no events exist
     * @throws TaskStoreException if the operation fails
     */
    List<TaskEvent> loadEvents(TaskId taskId);

    /**
     * Saves a checkpoint for a task, replacing any previous checkpoint.
     *
     * @param checkpoint the checkpoint to save
     * @throws TaskStoreException if the operation fails
     */
    void saveCheckpoint(Checkpoint checkpoint);

    /**
     * Loads the most recent checkpoint for a task.
     *
     * @param taskId the task identifier
     * @return the most recent checkpoint, or empty if none exists
     * @throws TaskStoreException if the operation fails
     */
    Optional<Checkpoint> loadCheckpoint(TaskId taskId);

    /**
     * Returns the identifiers of all known tasks.
     *
     * @return set of task identifiers
     * @throws TaskStoreException if the operation fails
     */
    Set<TaskId> getAllTaskIds();

    /**
     * Returns the identifiers of all tasks in the given status.
     *
     * @param status the status to filter by
     * @return set of matching task identifiers
     * @throws TaskStoreException if the operation fails
     */
    Set<TaskId> getTaskIdsByStatus(TaskStatus status);

    /**
     * Deletes all data (metadata, events, checkpoint) for a task.
     *
     * @param taskId the task identifier
     * @return {@code true} if the task existed and was deleted, {@code false} otherwise
     * @throws TaskStoreException if the operation fails
     */
    boolean delete(TaskId taskId);

    /**
     * Atomically loads the metadata from the store, verifies that the current status
     * matches {@code expectedStatus}, transitions to {@code newStatus}, and persists
     * the updated metadata — all as a single atomic operation with respect to the store.
     *
     * <p>This method is the store-level compare-and-set that prevents races between
     * concurrent cancel and complete operations when the store deserializes metadata
     * into new instances (e.g., {@code FileTaskExecutionStore}). Callers should use
     * this method instead of loading metadata, calling
     * {@link TaskMetadata#compareAndTransition}, and saving separately.
     *
     * <p>The default implementation is <em>not</em> atomic across load and save.
     * Concrete implementations should override to provide true atomicity.
     *
     * @param taskId         the task identifier
     * @param expectedStatus the status the task must currently be in
     * @param newStatus      the target status
     * @return the updated metadata if the transition succeeded, or empty if the task
     *         was not found or its status did not match {@code expectedStatus}
     * @throws TaskStoreException if the operation fails
     */
    default Optional<TaskMetadata> compareAndSetStatus(TaskId taskId, TaskStatus expectedStatus, TaskStatus newStatus) {
        return compareAndSetStatus(taskId, expectedStatus, newStatus, null);
    }

    /**
     * Atomically loads the metadata from the store, verifies that the current status
     * matches {@code expectedStatus}, transitions to {@code newStatus} with the given
     * failure reason, and persists the updated metadata.
     *
     * @param taskId         the task identifier
     * @param expectedStatus the status the task must currently be in
     * @param newStatus      the target status
     * @param failureReason  optional failure reason (only meaningful for {@link TaskStatus#FAILED})
     * @return the updated metadata if the transition succeeded, or empty if the task
     *         was not found or its status did not match {@code expectedStatus}
     * @throws TaskStoreException if the operation fails
     */
    default Optional<TaskMetadata> compareAndSetStatus(
            TaskId taskId, TaskStatus expectedStatus, TaskStatus newStatus, String failureReason) {
        Optional<TaskMetadata> loaded = loadMetadata(taskId);
        if (loaded.isEmpty()) {
            return Optional.empty();
        }
        TaskMetadata metadata = loaded.get();
        if (!metadata.compareAndTransition(expectedStatus, newStatus, failureReason)) {
            return Optional.empty();
        }
        saveMetadata(metadata);
        return Optional.of(metadata);
    }
}
