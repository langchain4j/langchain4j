package dev.langchain4j.experimental.durable.store.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Experimental;
import dev.langchain4j.experimental.durable.task.TaskId;
import java.time.Instant;

/**
 * Recorded when a task completes successfully.
 */
@Experimental
public record TaskCompletedEvent(TaskId taskId, Instant timestamp, String serializedResult) implements TaskEvent {

    @JsonCreator
    public TaskCompletedEvent(
            @JsonProperty("taskId") TaskId taskId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("serializedResult") String serializedResult) {
        this.taskId = taskId;
        this.timestamp = timestamp;
        this.serializedResult = serializedResult;
    }
}
