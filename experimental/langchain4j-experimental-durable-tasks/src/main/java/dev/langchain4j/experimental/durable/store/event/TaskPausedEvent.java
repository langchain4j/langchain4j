package dev.langchain4j.experimental.durable.store.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Experimental;
import dev.langchain4j.experimental.durable.task.TaskId;
import java.time.Instant;

/**
 * Recorded when a task is paused, typically to wait for human input.
 */
@Experimental
public record TaskPausedEvent(TaskId taskId, Instant timestamp, String reason, String pendingAgentName)
        implements TaskEvent {

    @JsonCreator
    public TaskPausedEvent(
            @JsonProperty("taskId") TaskId taskId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("reason") String reason,
            @JsonProperty("pendingAgentName") String pendingAgentName) {
        this.taskId = taskId;
        this.timestamp = timestamp;
        this.reason = reason;
        this.pendingAgentName = pendingAgentName;
    }
}
