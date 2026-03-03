package dev.langchain4j.experimental.durable.store.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Experimental;
import dev.langchain4j.experimental.durable.task.TaskId;
import java.time.Instant;
import java.util.Map;

/**
 * Recorded when an agent invocation begins within a task.
 */
@Experimental
public record AgentInvocationStartedEvent(
        TaskId taskId, Instant timestamp, String agentName, String agentId, Map<String, Object> inputs)
        implements TaskEvent {

    @JsonCreator
    public AgentInvocationStartedEvent(
            @JsonProperty("taskId") TaskId taskId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("agentName") String agentName,
            @JsonProperty("agentId") String agentId,
            @JsonProperty("inputs") Map<String, Object> inputs) {
        this.taskId = taskId;
        this.timestamp = timestamp;
        this.agentName = agentName;
        this.agentId = agentId;
        this.inputs = inputs != null ? Map.copyOf(inputs) : Map.of();
    }
}
