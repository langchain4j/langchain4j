package dev.langchain4j.agentic.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A {@link DeferredResponse} that suspends the agentic system instead of blocking.
 * <p>
 * When the agentic system encounters this response, it checkpoints its state (if a
 * {@link dev.langchain4j.agentic.scope.AgenticScopeStore} is configured) and throws
 * {@link dev.langchain4j.agentic.scope.AgenticSystemSuspendedException}, releasing the
 * calling thread. The system can be resumed later by completing the response via
 * {@link dev.langchain4j.agentic.scope.AgenticScope#completePendingResponse(String, Object)}
 * and re-invoking the agent method with the same memory ID.
 * <p>
 * Usage with {@link dev.langchain4j.agentic.workflow.HumanInTheLoop}:
 * <pre>{@code
 * HumanInTheLoop.builder()
 *     .responseProvider(scope -> new SuspendedResponse<>("user-approval"))
 *     .build();
 *
 * // After suspension, from an external system:
 * scope.completePendingResponse("user-approval", "approved");
 * // Then re-invoke the agent method with the same memory ID
 * }</pre>
 *
 * @param <T> the type of the response value
 * @see PendingResponse for thread-blocking behavior (future-based)
 */
public class SuspendedResponse<T> extends DeferredResponse<T> {

    @JsonCreator
    public SuspendedResponse(@JsonProperty("responseId") String responseId) {
        super(responseId);
    }
}
