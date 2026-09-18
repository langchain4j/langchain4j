package dev.langchain4j.agentic.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A {@link DeferredResponse} that blocks the calling thread until completed.
 * <p>
 * When the agentic system encounters this response via {@code readState()}, the calling thread
 * blocks on the underlying {@link java.util.concurrent.CompletableFuture} until
 * {@link #complete(Object)} is called externally. This is the default behavior for
 * human-in-the-loop agents that do not require crash-resilient suspension.
 * <p>
 * Usage with {@link dev.langchain4j.agentic.workflow.HumanInTheLoop}:
 * <pre>{@code
 * HumanInTheLoop.builder()
 *     .responseProvider(scope -> new PendingResponse<>("user-approval"))
 *     .build();
 *
 * // From another thread:
 * scope.completePendingResponse("user-approval", "approved");
 * }</pre>
 *
 * @param <T> the type of the response value
 * @see SuspendedResponse for crash-resilient suspension (exception-based)
 */
public class PendingResponse<T> extends DeferredResponse<T> {

    @JsonCreator
    public PendingResponse(@JsonProperty("responseId") String responseId) {
        super(responseId);
    }
}
