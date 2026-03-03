package dev.langchain4j.experimental.durable.store.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Experimental;
import dev.langchain4j.experimental.durable.task.TaskId;
import java.time.Instant;

/**
 * Recorded when a task is explicitly cancelled.
 */
@Experimental
public record TaskCancelledEvent(TaskId taskId, Instant timestamp) implements TaskEvent {

    @JsonCreator
    public TaskCancelledEvent(@JsonProperty("taskId") TaskId taskId, @JsonProperty("timestamp") Instant timestamp) {
        this.taskId = taskId;
        this.timestamp = timestamp;
    }
}
