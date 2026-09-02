package dev.langchain4j.agentic.a2a;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.exception.LangChain4jException;
import java.util.Map;
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
public class A2ATaskInterruptedException extends LangChain4jException {

    private final String taskId;
    private final String contextId;
    private final TaskState state;
    private final String reason;

    public A2ATaskInterruptedException(String taskId, TaskState state, String reason) {
        this(taskId, null, state, reason);
    }

    public A2ATaskInterruptedException(String taskId, String contextId, TaskState state, String reason) {
        super("A2A task " + taskId + " is interrupted in state " + state.name() + ": "
                + (reason == null || reason.isEmpty() ? defaultReason(state) : reason));
        this.taskId = taskId;
        this.contextId = contextId;
        this.state = state;
        this.reason = reason == null || reason.isEmpty() ? null : reason;
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
     * The id of the context containing the interrupted task. This can be used together with
     * {@link #taskId()} when sending the input or authentication needed to resume the task.
     *
     * @since 1.19.0
     */
    public String contextId() {
        return contextId;
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

    /**
     * The status message sent by the remote agent when it interrupted the task — typically the
     * question asking for the missing input, or the authentication challenge. Returns {@code null}
     * when the remote agent interrupted the task without sending one; the generic description used
     * in {@link #getMessage()} in that case is not reported here, so callers can tell the two apart.
     *
     * @since 1.19.0
     */
    public String reason() {
        return reason;
    }

    /**
     * Reconstructs the A2A interruption associated with a pending response in an agentic scope.
     * This allows an external event handler to publish the remote task details before completing
     * the pending response asynchronously.
     *
     * @param scope the suspended agentic scope
     * @param responseId the pending response identifier
     * @return the matching A2A task interruption
     * @throws IllegalArgumentException if the response does not belong to an interrupted A2A task
     * @since 1.19.0
     */
    public static A2ATaskInterruptedException from(AgenticScope scope, String responseId) {
        return scope.state().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(DefaultA2AClientBuilder.INTERRUPTION_STATE_PREFIX))
                .map(Map.Entry::getValue)
                .filter(Map.class::isInstance)
                .map(A2ATaskInterruptedException::metadata)
                .filter(value -> responseId.equals(value.get("responseId")))
                .findFirst()
                .map(A2ATaskInterruptedException::fromMetadata)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No interrupted A2A task found for pending response " + responseId));
    }

    static A2ATaskInterruptedException fromMetadata(Object value) {
        Map<?, ?> metadata = metadata(value);
        return new A2ATaskInterruptedException(
                (String) metadata.get("taskId"),
                (String) metadata.get("contextId"),
                TaskState.valueOf((String) metadata.get("state")),
                (String) metadata.get("reason"));
    }

    private static Map<?, ?> metadata(Object value) {
        if (value instanceof Map<?, ?> metadata) {
            return metadata;
        }
        throw new IllegalArgumentException("Invalid interrupted A2A task metadata");
    }

    private static String defaultReason(TaskState state) {
        return state == TaskState.TASK_STATE_AUTH_REQUIRED
                ? "waiting for authentication"
                : "waiting for additional input";
    }
}
