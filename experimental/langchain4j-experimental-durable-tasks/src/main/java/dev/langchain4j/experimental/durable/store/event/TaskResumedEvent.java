package dev.langchain4j.experimental.durable.store.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Experimental;
import dev.langchain4j.experimental.durable.task.TaskId;
import java.time.Instant;
import java.util.Map;

/**
 * Recorded when a paused task is resumed, optionally with user-provided input.
 */
@Experimental
public record TaskResumedEvent(TaskId taskId, Instant timestamp, Map<String, Object> userInput) implements TaskEvent {

    @JsonCreator
    public TaskResumedEvent(
            @JsonProperty("taskId") TaskId taskId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("userInput") Map<String, Object> userInput) {
        this.taskId = taskId;
        this.timestamp = timestamp;
        this.userInput = userInput != null ? Map.copyOf(userInput) : Map.of();
    }
}
