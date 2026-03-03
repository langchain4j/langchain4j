package dev.langchain4j.experimental.durable.task;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.LangChain4jException;

/**
 * Thrown to signal that a task should be paused.
 *
 * <p>Common pause reasons include waiting for human input, waiting for an
 * external approval, or explicitly pausing a long-running batch. The
 * exception propagates naturally through the planner loop and is caught by
 * {@link dev.langchain4j.experimental.durable.LongLivedTaskService}, which transitions the task to
 * {@link TaskStatus#PAUSED} and saves a checkpoint.
 *
 * <p>This mechanism requires zero changes to the existing agentic runtime.
 */
@Experimental
public class TaskPausedException extends LangChain4jException {

    private final String reason;
    private final String pendingOutputKey;

    /**
     * Creates a pause exception with a reason and an associated scope key.
     *
     * @param reason           the human-readable reason for pausing
     * @param pendingOutputKey the scope key that is awaiting a value, or {@code null}
     */
    public TaskPausedException(String reason, String pendingOutputKey) {
        super("Task paused: " + reason);
        this.reason = reason;
        this.pendingOutputKey = pendingOutputKey;
    }

    /**
     * Creates a pause exception with a reason and no associated scope key.
     *
     * @param reason the human-readable reason for pausing
     */
    public TaskPausedException(String reason) {
        this(reason, null);
    }

    /**
     * Returns the reason for pausing.
     *
     * @return the pause reason
     */
    public String reason() {
        return reason;
    }

    /**
     * Returns the scope state key that is awaiting a value, or {@code null}
     * if the pause is not tied to a specific scope key.
     *
     * @return the pending output key, or null
     */
    public String pendingOutputKey() {
        return pendingOutputKey;
    }
}
