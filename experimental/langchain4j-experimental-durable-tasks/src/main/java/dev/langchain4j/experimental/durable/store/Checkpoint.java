package dev.langchain4j.experimental.durable.store;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Experimental;
import dev.langchain4j.experimental.durable.task.TaskId;
import dev.langchain4j.experimental.durable.task.TaskMetadata;
import java.time.Instant;

/**
 * An immutable snapshot of a task's state at a specific point in time.
 *
 * <p>A checkpoint captures:
 * <ul>
 *   <li>The task metadata (status, agent name, labels, timestamps)</li>
 *   <li>The serialized scope state (agent invocation data as JSON)</li>
 *   <li>The number of journal events replayed to reach this state</li>
 *   <li>The timestamp when this checkpoint was taken</li>
 * </ul>
 *
 * <p>Checkpoints are used during resume to fast-forward the agent topology to the
 * last-known good state, avoiding re-execution of already-completed invocations.
 *
 * @param taskId         the identifier of the task
 * @param metadata       the task metadata at checkpoint time
 * @param serializedScope the scope state serialized as JSON, or {@code null} if serialization failed
 * @param eventCount     the number of events in the journal at checkpoint time
 * @param createdAt      the timestamp when this checkpoint was created
 */
@Experimental
public record Checkpoint(
        TaskId taskId, TaskMetadata metadata, String serializedScope, int eventCount, Instant createdAt) {

    @JsonCreator
    public Checkpoint(
            @JsonProperty("taskId") TaskId taskId,
            @JsonProperty("metadata") TaskMetadata metadata,
            @JsonProperty("serializedScope") String serializedScope,
            @JsonProperty("eventCount") int eventCount,
            @JsonProperty("createdAt") Instant createdAt) {
        this.taskId = ensureNotNull(taskId, "taskId");
        this.metadata = ensureNotNull(metadata, "metadata");
        this.serializedScope = serializedScope;
        this.eventCount = eventCount;
        this.createdAt = ensureNotNull(createdAt, "createdAt");
    }
}
