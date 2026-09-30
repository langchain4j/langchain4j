package dev.langchain4j.agentic.internal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public interface DelayedResponse<T> {

    boolean isDone();

    T blockingGet();

    default Object result() {
        return isDone() ? blockingGet() : "<pending>";
    }

    /**
     * Blocks on {@code future} and, on failure, rethrows the original cause instead of the
     * {@link CompletionException} that {@link CompletableFuture#join()} wraps it in, so an
     * asynchronous agent failure surfaces the same exception type as the synchronous path (an
     * {@link dev.langchain4j.agentic.agent.AgentInvocationException}) instead of leaking the
     * {@code CompletableFuture} plumbing. A checked-exception cause cannot be rethrown from here
     * and is left wrapped.
     */
    static <R> R join(CompletableFuture<R> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }
}
