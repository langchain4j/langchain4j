package dev.langchain4j.experimental.durable.store;

import dev.langchain4j.Experimental;
import dev.langchain4j.experimental.durable.store.event.TaskEvent;
import dev.langchain4j.experimental.durable.task.TaskId;
import dev.langchain4j.experimental.durable.task.TaskMetadata;
import dev.langchain4j.experimental.durable.task.TaskStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link TaskExecutionStore} backed by concurrent hash maps.
 *
 * <p>This implementation is suitable for testing and development. All data is lost when
 * the JVM exits. For production use, consider {@code FileTaskExecutionStore} or a
 * custom database-backed implementation.
 *
 * <p>All operations are thread-safe.
 */
@Experimental
public class InMemoryTaskExecutionStore implements TaskExecutionStore {

    private final ConcurrentHashMap<TaskId, TaskMetadata> metadataMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TaskId, List<TaskEvent>> eventsMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TaskId, Checkpoint> checkpointMap = new ConcurrentHashMap<>();

    @Override
    public void saveMetadata(TaskMetadata metadata) {
        metadataMap.put(metadata.id(), metadata);
    }

    @Override
    public Optional<TaskMetadata> loadMetadata(TaskId taskId) {
        return Optional.ofNullable(metadataMap.get(taskId));
    }

    @Override
    public void appendEvent(TaskEvent event) {
        eventsMap
                .computeIfAbsent(event.taskId(), id -> Collections.synchronizedList(new ArrayList<>()))
                .add(event);
    }

    @Override
    public List<TaskEvent> loadEvents(TaskId taskId) {
        List<TaskEvent> events = eventsMap.get(taskId);
        if (events == null) {
            return List.of();
        }
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    @Override
    public void saveCheckpoint(Checkpoint checkpoint) {
        checkpointMap.put(checkpoint.taskId(), checkpoint);
    }

    @Override
    public Optional<Checkpoint> loadCheckpoint(TaskId taskId) {
        Checkpoint cp = checkpointMap.get(taskId);
        if (cp == null) {
            return Optional.empty();
        }
        // Return a defensive copy so callers cannot mutate the stored snapshot
        TaskMetadata metaSnapshot = TaskMetadata.of(
                cp.metadata().id(),
                cp.metadata().agentName(),
                cp.metadata().status(),
                cp.metadata().createdAt(),
                cp.metadata().updatedAt(),
                cp.metadata().failureReason(),
                cp.metadata().labels());
        return Optional.of(
                new Checkpoint(cp.taskId(), metaSnapshot, cp.serializedScope(), cp.eventCount(), cp.createdAt()));
    }

    @Override
    public Set<TaskId> getAllTaskIds() {
        return Set.copyOf(metadataMap.keySet());
    }

    @Override
    public Set<TaskId> getTaskIdsByStatus(TaskStatus status) {
        return metadataMap.values().stream()
                .filter(metadata -> metadata.status() == status)
                .map(TaskMetadata::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean delete(TaskId taskId) {
        boolean existed = metadataMap.remove(taskId) != null;
        eventsMap.remove(taskId);
        checkpointMap.remove(taskId);
        return existed;
    }

    @Override
    public Optional<TaskMetadata> compareAndSetStatus(
            TaskId taskId, TaskStatus expectedStatus, TaskStatus newStatus, String failureReason) {
        TaskMetadata metadata = metadataMap.get(taskId);
        if (metadata == null) {
            return Optional.empty();
        }
        // In-memory store shares the same object instance, so the synchronized
        // compareAndTransition on TaskMetadata is sufficient for atomicity.
        if (!metadata.compareAndTransition(expectedStatus, newStatus, failureReason)) {
            return Optional.empty();
        }
        return Optional.of(metadata);
    }
}
