package dev.langchain4j.invocation;

import dev.langchain4j.Experimental;
import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;

/**
 * Makes sure that an AI Service invocation reports its end only once, with exactly one of
 * {@link AiServiceCompletedEvent},
 * {@link AiServiceErrorEvent} or
 * {@link AiServiceCancelledEvent}, also when completion, failure and
 * cancellation race.
 * <p>
 * AI Services call it before firing one of these events. An integration that implements AI Services itself and fires
 * these events on its own should do the same:
 * <pre>{@code
 * if (InvocationEnd.markEnded(invocationContext)) {
 *     listenerRegistrar.fireEvent(AiServiceCompletedEvent.builder()
 *             .invocationContext(invocationContext)
 *             .result(result)
 *             .build());
 * }
 * }</pre>
 * The state is kept in the {@link DefaultInvocationContext} and is shared by all its copies made with
 * {@link InvocationContext#toBuilder()} that keep the same {@link InvocationContext#invocationId()}.
 *
 * @since 1.23.0
 */
@Experimental
public final class InvocationEnd {

    private InvocationEnd() {}

    /**
     * Marks the given invocation as ended.
     *
     * @param invocationContext the context of the invocation
     * @return {@code true} the first time it is called for the given invocation, so the caller fires the end event;
     *         {@code false} afterwards, so the caller fires nothing. Always {@code true} for an invocation without id
     *         or with an {@link InvocationContext} that is not a {@link DefaultInvocationContext}.
     */
    public static boolean markEnded(InvocationContext invocationContext) {
        if (invocationContext instanceof DefaultInvocationContext defaultInvocationContext
                && invocationContext.invocationId() != null) {
            return defaultInvocationContext.ended().compareAndSet(false, true);
        }
        return true;
    }
}
