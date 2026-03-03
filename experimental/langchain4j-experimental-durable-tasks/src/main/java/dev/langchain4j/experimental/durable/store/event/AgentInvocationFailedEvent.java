package dev.langchain4j.experimental.durable.store.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Experimental;
import dev.langchain4j.experimental.durable.task.TaskId;
import java.time.Instant;

/**
 * Recorded when an agent invocation fails with an error within a task.
 */
@Experimental
public record AgentInvocationFailedEvent(
        TaskId taskId, Instant timestamp, String agentName, String agentId, String errorMessage, String stackTrace)
        implements TaskEvent {

    @JsonCreator
    public AgentInvocationFailedEvent(
            @JsonProperty("taskId") TaskId taskId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("agentName") String agentName,
            @JsonProperty("agentId") String agentId,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("stackTrace") String stackTrace) {
        this.taskId = taskId;
        this.timestamp = timestamp;
        this.agentName = agentName;
        this.agentId = agentId;
        this.errorMessage = errorMessage;
        this.stackTrace = stackTrace;
    }
}
