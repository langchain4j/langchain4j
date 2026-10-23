package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.invocation.InvocationContext;
import java.util.function.Supplier;

/**
 * Holds the {@link InvocationContext} of the AI Service invocation that is doing its own work on the current thread
 * (running a tool, a guardrail, retrieval or the {@code chatRequestTransformer}), so that an AI Service invoked by that
 * work can record it as its parent invocation.
 * <p>
 * It is set only around that work, never around code of the caller (the handlers of a {@code TokenStream}, the
 * subscriber of a returned publisher, the continuations of a returned future): an AI Service invoked by the caller's
 * code has no parent invocation.
 */
@Internal
public final class CurrentInvocation {

    private static final ThreadLocal<InvocationContext> CURRENT = new ThreadLocal<>();

    private CurrentInvocation() {}

    public static InvocationContext get() {
        return CURRENT.get();
    }

    /**
     * Calls the given action with the given invocation context as the current one, then restores the previous one.
     * Does nothing special when the invocation context is {@code null}.
     */
    public static <T> T callWith(InvocationContext invocationContext, Supplier<T> action) {
        if (invocationContext == null) {
            return action.get();
        }
        InvocationContext previous = CURRENT.get();
        CURRENT.set(invocationContext);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
