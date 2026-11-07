package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.ChatRequestOptions;
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
     * @return the invocation id that the listeners of a chat model call get: the one in the given options (which can
     *         be {@code null}) if any, otherwise the current one, otherwise {@code null}.
     */
    public static UUID invocationId(ChatRequestOptions options) {
        return options != null && options.invocationId() != null ? options.invocationId() : invocationId();
    }

    /**
     * Runs the given action with the id of the given invocation context as the current one, then restores the
     * previous one. Does nothing special when the invocation context is {@code null}.
     */
    public static void runWith(InvocationContext invocationContext, Runnable action) {
        callWith(invocationContext, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Calls the given action with the id of the given invocation context as the current one, then restores the
     * previous one. Does nothing special when the invocation context is {@code null}.
     */
    public static <T> T callWith(InvocationContext invocationContext, Supplier<T> action) {
        return callWithId(invocationContext == null ? null : invocationContext.invocationId(), action);
    }

    /**
     * Calls the given action with the given invocation id as the current one, then restores the previous one. Does
     * nothing special when the invocation id is {@code null}.
     */
    public static <T> T callWithId(UUID invocationId, Supplier<T> action) {
        if (invocationId == null) {
            return action.get();
        }
        UUID previous = CURRENT_INVOCATION_ID.get();
        CURRENT_INVOCATION_ID.set(invocationId);
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
     * Calls the given action with the given invocation id as the current one, or with no current invocation when the
     * id is {@code null}, then restores the previous one. Used where an id captured earlier must apply exactly, also
     * when it is {@code null}.
     */
    public static <T> T callWithIdOrWithout(UUID invocationId, Supplier<T> action) {
        return invocationId == null ? callWithout(action) : callWithId(invocationId, action);
    }

    /**
     * Runs the given action like {@link #callWithIdOrWithout(UUID, Supplier)}.
     */
    public static void runWithIdOrWithout(UUID invocationId, Runnable action) {
        callWithIdOrWithout(invocationId, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Runs the given action with no current invocation, then restores the previous one. Used to notify listeners:
     * their code is not part of the work of the invocation.
     */
    public static void runWithout(Runnable action) {
        callWithout(() -> {
            action.run();
            return null;
        });
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
