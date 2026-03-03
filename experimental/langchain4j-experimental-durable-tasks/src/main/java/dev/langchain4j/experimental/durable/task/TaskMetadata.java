package dev.langchain4j.experimental.durable.task;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Experimental;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Metadata describing a durable long-lived task: identity, status, timestamps, and optional labels.
 *
 * <p>This is a mutable holder intentionally — status and timestamps are updated throughout the task lifecycle.
 * Thread safety for state transitions is provided via {@code synchronized} methods.
 */
@Experimental
public final class TaskMetadata {

    private final TaskId id;
    private final String agentName;
    private volatile TaskStatus status;
    private final Instant createdAt;
    private volatile Instant updatedAt;
    private volatile String failureReason;
    private final Map<String, String> labels;

    @JsonCreator
    private TaskMetadata(
            @JsonProperty("id") TaskId id,
            @JsonProperty("agentName") String agentName,
            @JsonProperty("status") TaskStatus status,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("updatedAt") Instant updatedAt,
            @JsonProperty("failureReason") String failureReason,
            @JsonProperty("labels") Map<String, String> labels) {
        this.id = ensureNotNull(id, "id");
        this.agentName = ensureNotBlank(agentName, "agentName");
        this.status = ensureNotNull(status, "status");
        this.createdAt = ensureNotNull(createdAt, "createdAt");
        this.updatedAt = ensureNotNull(updatedAt, "updatedAt");
        this.failureReason = failureReason;
        this.labels = Collections.unmodifiableMap(new LinkedHashMap<>(ensureNotNull(labels, "labels")));
    }

    /**
     * Creates a new TaskMetadata in {@link TaskStatus#PENDING} state.
     *
     * @param id        the unique task identifier
     * @param agentName a human-readable name for the agent or workflow
     * @param labels    optional key-value labels for user metadata
     * @return a new TaskMetadata
     */
    public static TaskMetadata create(TaskId id, String agentName, Map<String, String> labels) {
        Instant now = Instant.now();
        return new TaskMetadata(id, agentName, TaskStatus.PENDING, now, now, null, labels);
    }

    /**
     * Creates a TaskMetadata from stored values (e.g., deserialized from a file).
     */
    public static TaskMetadata of(
            TaskId id,
            String agentName,
            TaskStatus status,
            Instant createdAt,
            Instant updatedAt,
            String failureReason,
            Map<String, String> labels) {
        return new TaskMetadata(id, agentName, status, createdAt, updatedAt, failureReason, labels);
    }

    @JsonProperty("id")
    public TaskId id() {
        return id;
    }

    @JsonProperty("agentName")
    public String agentName() {
        return agentName;
    }

    @JsonProperty("status")
    public TaskStatus status() {
        return status;
    }

    @JsonProperty("createdAt")
    public Instant createdAt() {
        return createdAt;
    }

    @JsonProperty("updatedAt")
    public Instant updatedAt() {
        return updatedAt;
    }

    @JsonProperty("failureReason")
    public String failureReason() {
        return failureReason;
    }

    @JsonProperty("labels")
    public Map<String, String> labels() {
        return labels;
    }

    /**
     * Transitions this task to the given status, updating the timestamp.
     *
     * @param newStatus the new status
     */
    public synchronized void transitionTo(TaskStatus newStatus) {
        transitionTo(newStatus, null);
    }

    /**
     * Transitions this task to the given status with a failure reason, updating the timestamp.
     *
     * @param newStatus     the new status
     * @param failureReason optional failure reason (only meaningful for {@link TaskStatus#FAILED})
     */
    public synchronized void transitionTo(TaskStatus newStatus, String failureReason) {
        if (this.status.isTerminal()) {
            throw new IllegalStateException("Cannot transition from terminal state " + this.status);
        }
        this.status = ensureNotNull(newStatus, "newStatus");
        this.failureReason = failureReason;
        this.updatedAt = Instant.now();
    }

    /**
     * Atomically transitions from {@code expectedStatus} to {@code newStatus} if and only if the
     * current status matches the expected value. This is the compare-and-set guard that prevents
     * race conditions between concurrent cancel and complete operations.
     *
     * @param expectedStatus the status expected before the transition
     * @param newStatus      the target status
     * @return {@code true} if the transition succeeded, {@code false} if the current status
     *         did not match {@code expectedStatus}
     */
    public synchronized boolean compareAndTransition(TaskStatus expectedStatus, TaskStatus newStatus) {
        return compareAndTransition(expectedStatus, newStatus, null);
    }

    /**
     * Atomically transitions from {@code expectedStatus} to {@code newStatus} with a failure reason,
     * if and only if the current status matches the expected value.
     *
     * @param expectedStatus the status expected before the transition
     * @param newStatus      the target status
     * @param failureReason  optional failure reason
     * @return {@code true} if the transition succeeded, {@code false} if the current status
     *         did not match {@code expectedStatus}
     */
    public synchronized boolean compareAndTransition(
            TaskStatus expectedStatus, TaskStatus newStatus, String failureReason) {
        if (this.status != expectedStatus) {
            return false;
        }
        // COMPLETED and CANCELLED are hard-terminal: no further transitions allowed.
        // FAILED is soft-terminal: allows FAILED→RUNNING for resume.
        if (expectedStatus == TaskStatus.COMPLETED || expectedStatus == TaskStatus.CANCELLED) {
            return false;
        }
        this.status = ensureNotNull(newStatus, "newStatus");
        this.failureReason = failureReason;
        this.updatedAt = Instant.now();
        return true;
    }

    @Override
    public String toString() {
        return "TaskMetadata{id=" + id
                + ", agentName='" + agentName + '\''
                + ", status=" + status
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + (failureReason != null ? ", failureReason='" + failureReason + '\'' : "")
                + '}';
    }
}
