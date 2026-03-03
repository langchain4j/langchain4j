package dev.langchain4j.experimental.durable.store.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Experimental;
import dev.langchain4j.experimental.durable.task.TaskId;
import java.time.Instant;

/**
 * Recorded when a task fails with an error.
 */
@Experimental
public record TaskFailedEvent(TaskId taskId, Instant timestamp, String errorMessage, String stackTrace)
        implements TaskEvent {

    @JsonCreator
    public TaskFailedEvent(
            @JsonProperty("taskId") TaskId taskId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("stackTrace") String stackTrace) {
        this.taskId = taskId;
        this.timestamp = timestamp;
        this.errorMessage = errorMessage;
        this.stackTrace = stackTrace;
    }
}
