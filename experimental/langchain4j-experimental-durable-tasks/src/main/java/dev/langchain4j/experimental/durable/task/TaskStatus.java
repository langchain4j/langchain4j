package dev.langchain4j.experimental.durable.task;

import dev.langchain4j.Experimental;

/**
 * Lifecycle status of a durable long-lived task.
 */
@Experimental
public enum TaskStatus {

    /** Task has been created but not yet started. */
    PENDING,

    /** Task is currently executing. */
    RUNNING,

    /** Task is paused, awaiting external input or an explicit resume. */
    PAUSED,

    /** Task is waiting before an automatic retry after a transient failure. */
    RETRYING,

    /** Task execution failed with an error. */
    FAILED,

    /** Task completed successfully. */
    COMPLETED,

    /** Task was explicitly cancelled. */
    CANCELLED;

    /**
     * Returns {@code true} if the task is in a terminal state ({@link #COMPLETED},
     * {@link #FAILED}, or {@link #CANCELLED}).
     *
     * <p>{@link #COMPLETED} and {@link #CANCELLED} are <em>hard-terminal</em> — no further
     * transitions are possible. {@link #FAILED} is <em>soft-terminal</em> — it can be
     * resumed back to {@link #RUNNING} via
     * {@link dev.langchain4j.experimental.durable.LongLivedTaskService#resume}.
     *
     * @return true if terminal
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
