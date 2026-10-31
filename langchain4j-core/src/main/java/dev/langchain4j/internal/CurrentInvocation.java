package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.invocation.InvocationContext;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Holds the {@link InvocationContext#invocationId()} of the AI Service invocation that is doing its own work on the
 * current thread (for example running a tool, a guardrail, retrieval or the {@code chatRequestTransformer}), so that an AI Service
 * invoked by that work can record it as its parent invocation.
 * <p>
 * It is set only around that work, not around code of the caller (the handlers of a {@code TokenStream}, the
 * subscriber of a returned publisher, the continuations of a returned future), so an AI Service invoked by the
 * caller's code normally has no parent invocation.
 */
@Internal
public final class CurrentInvocation {

    private static final ThreadLocal<UUID> CURRENT_INVOCATION_ID = new ThreadLocal<>();

    private CurrentInvocation() {}

    public static UUID invocationId() {
        return CURRENT_INVOCATION_ID.get();
    }

    /**
     * Calls the given action with the id of the given invocation context as the current one, then restores the
     * previous one. Does nothing special when the invocation context is {@code null}.
     */
    public static <T> T callWith(InvocationContext invocationContext, Supplier<T> action) {
        if (invocationContext == null) {
            return action.get();
        }
        UUID previous = CURRENT_INVOCATION_ID.get();
        CURRENT_INVOCATION_ID.set(invocationContext.invocationId());
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT_INVOCATION_ID.remove();
            } else {
                CURRENT_INVOCATION_ID.set(previous);
            }
        }
    }

    /**
     * Calls the given action with no current invocation, then restores the previous one. Used when an AI Service is
     * invoked during the work of another invocation: its listeners and callbacks are not part of that work.
     */
    public static <T> T callWithout(Supplier<T> action) {
        UUID previous = CURRENT_INVOCATION_ID.get();
        if (previous == null) {
            return action.get();
        }
        CURRENT_INVOCATION_ID.remove();
        try {
            return action.get();
        } finally {
            CURRENT_INVOCATION_ID.set(previous);
        }
    }
}
