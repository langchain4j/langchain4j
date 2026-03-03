package dev.langchain4j.experimental.durable.store.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.langchain4j.Experimental;
import dev.langchain4j.experimental.durable.task.TaskId;
import java.time.Instant;

/**
 * Sealed interface for all events in a task's execution journal.
 *
 * <p>Events are appended to an ordered journal and enable:
 * <ul>
 *   <li>Reconstructing execution history for debugging and observability</li>
 *   <li>Determining the resume point after a crash</li>
 *   <li>Auditing all agent invocations and their outcomes</li>
 * </ul>
 *
 * <p>All events carry a {@link #schemaVersion()} for forward compatibility.
 * Consumers should tolerate unknown fields gracefully.
 */
@Experimental
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TaskStartedEvent.class, name = "task_started"),
    @JsonSubTypes.Type(value = AgentInvocationStartedEvent.class, name = "agent_invocation_started"),
    @JsonSubTypes.Type(value = AgentInvocationCompletedEvent.class, name = "agent_invocation_completed"),
    @JsonSubTypes.Type(value = AgentInvocationFailedEvent.class, name = "agent_invocation_failed"),
    @JsonSubTypes.Type(value = TaskPausedEvent.class, name = "task_paused"),
    @JsonSubTypes.Type(value = TaskResumedEvent.class, name = "task_resumed"),
    @JsonSubTypes.Type(value = TaskCompletedEvent.class, name = "task_completed"),
    @JsonSubTypes.Type(value = TaskFailedEvent.class, name = "task_failed"),
    @JsonSubTypes.Type(value = TaskCancelledEvent.class, name = "task_cancelled"),
    @JsonSubTypes.Type(value = TaskRetryEvent.class, name = "task_retry"),
})
public sealed interface TaskEvent
        permits TaskStartedEvent,
                AgentInvocationStartedEvent,
                AgentInvocationCompletedEvent,
                AgentInvocationFailedEvent,
                TaskPausedEvent,
                TaskResumedEvent,
                TaskCompletedEvent,
                TaskFailedEvent,
                TaskCancelledEvent,
                TaskRetryEvent {

    /**
     * Returns the identifier of the task this event belongs to.
     *
     * @return the task id
     */
    TaskId taskId();

    /**
     * Returns the timestamp when this event occurred.
     *
     * @return the event timestamp
     */
    Instant timestamp();

    /**
     * Returns the schema version for this event type. Starts at 1.
     * Consumers should use this to handle format evolution.
     *
     * @return the schema version
     */
    default int schemaVersion() {
        return 1;
    }
}
