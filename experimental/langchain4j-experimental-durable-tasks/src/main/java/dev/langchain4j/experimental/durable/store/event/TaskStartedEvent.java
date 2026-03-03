package dev.langchain4j.experimental.durable.store.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Experimental;
import dev.langchain4j.experimental.durable.task.TaskId;
import java.time.Instant;
import java.util.Map;

/**
 * Recorded when a task begins execution.
 */
@Experimental
public record TaskStartedEvent(TaskId taskId, Instant timestamp, Map<String, Object> initialInputs)
        implements TaskEvent {

    @JsonCreator
    public TaskStartedEvent(
            @JsonProperty("taskId") TaskId taskId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("initialInputs") Map<String, Object> initialInputs) {
        this.taskId = taskId;
        this.timestamp = timestamp;
        this.initialInputs = initialInputs != null ? Map.copyOf(initialInputs) : Map.of();
    }
}
