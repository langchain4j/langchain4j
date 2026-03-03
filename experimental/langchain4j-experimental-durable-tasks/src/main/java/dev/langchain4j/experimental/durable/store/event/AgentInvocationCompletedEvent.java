package dev.langchain4j.experimental.durable.store.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Experimental;
import dev.langchain4j.experimental.durable.task.TaskId;
import java.time.Instant;

/**
 * Recorded when an agent invocation completes successfully within a task.
 *
 * <p>The output is stored as a pre-serialized JSON string to avoid issues with
 * non-serializable types (e.g., {@code DelayedResponse}) in the scope state.
 */
@Experimental
public record AgentInvocationCompletedEvent(
        TaskId taskId, Instant timestamp, String agentName, String agentId, String serializedOutput)
        implements TaskEvent {

    @JsonCreator
    public AgentInvocationCompletedEvent(
            @JsonProperty("taskId") TaskId taskId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("agentName") String agentName,
            @JsonProperty("agentId") String agentId,
            @JsonProperty("serializedOutput") String serializedOutput) {
        this.taskId = taskId;
        this.timestamp = timestamp;
        this.agentName = agentName;
        this.agentId = agentId;
        this.serializedOutput = serializedOutput;
    }
}
