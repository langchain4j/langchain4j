package dev.langchain4j.experimental.durable.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.Internal;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.experimental.durable.store.Checkpoint;
import dev.langchain4j.experimental.durable.store.TaskExecutionStore;
import dev.langchain4j.experimental.durable.store.event.TaskEvent;
import dev.langchain4j.experimental.durable.task.TaskId;
import dev.langchain4j.experimental.durable.task.TaskMetadata;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal helper that creates and persists checkpoints for a running task.
 *
 * <p>Checkpoint creation never throws; failures are logged at WARN level so the
 * running task is never disrupted by a persistence failure.
 */
@Internal
public class CheckpointManager {

    private static final Logger LOG = LoggerFactory.getLogger(CheckpointManager.class);

    private final TaskExecutionStore store;
    private final ObjectMapper objectMapper;

    public CheckpointManager(TaskExecutionStore store) {
        this.store = store;
        this.objectMapper = ObjectMapperFactory.create();
    }

    /**
     * Takes a checkpoint of the current task state and persists it.
     *
     * @param taskId   the task identifier
     * @param metadata the current metadata
     * @param scope    the current agentic scope, or {@code null} if unavailable
     */
    public void checkpoint(TaskId taskId, TaskMetadata metadata, AgenticScope scope) {
        try {
            // Snapshot the metadata to guarantee checkpoint immutability
            TaskMetadata snapshot = TaskMetadata.of(
                    metadata.id(),
                    metadata.agentName(),
                    metadata.status(),
                    metadata.createdAt(),
                    metadata.updatedAt(),
                    metadata.failureReason(),
                    metadata.labels());
            String serializedScope = serializeScope(scope);
            List<TaskEvent> events = store.loadEvents(taskId);
            Checkpoint cp = new Checkpoint(taskId, snapshot, serializedScope, events.size(), Instant.now());
            store.saveCheckpoint(cp);
            LOG.debug("Checkpoint saved for task {} with {} events", taskId, events.size());
        } catch (Exception e) {
            LOG.warn("Failed to save checkpoint for task {}: {}", taskId, e.getMessage(), e);
        }
    }

    private String serializeScope(AgenticScope scope) {
        if (scope == null) {
            return null;
        }
        try {
            Map<String, Object> state = scope.state();
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to serialize scope state, checkpoint will have null scope: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            LOG.warn("Unexpected error serializing scope state: {}", e.getMessage());
            return null;
        }
    }
}
