package dev.langchain4j.agentic.a2a;

import org.a2aproject.sdk.spec.TaskState;

/**
 * Thrown when an A2A task enters an interrupted state ({@code input-required} or
 * {@code auth-required}) instead of completing.
 * <p>
 * The remote agent has paused the task and is waiting for additional input or
 * authentication before it can continue. The A2A server will not advance the task on
 * its own, so the {@link java.util.concurrent.CompletableFuture} backing the invocation
 * is completed exceptionally with this exception rather than left pending indefinitely.
 *
 * @since 1.19.0
 */
public class A2ATaskInterruptedException extends RuntimeException {

    private final String taskId;
    private final TaskState state;

    public A2ATaskInterruptedException(String taskId, TaskState state, String reason) {
        super("A2A task " + taskId + " is interrupted in state " + state.name()
                + (reason == null || reason.isEmpty() ? "" : ": " + reason));
        this.taskId = taskId;
        this.state = state;
    }

    /**
     * The id of the task that was interrupted.
     *
     * @since 1.19.0
     */
    public String taskId() {
        return taskId;
    }

    /**
     * The interrupted state the task is in, either {@code TASK_STATE_INPUT_REQUIRED} or
     * {@code TASK_STATE_AUTH_REQUIRED}.
     *
     * @since 1.19.0
     */
    public TaskState state() {
        return state;
    }
}
