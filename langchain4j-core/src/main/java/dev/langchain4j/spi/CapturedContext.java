package dev.langchain4j.spi;

import dev.langchain4j.Experimental;

/**
 * The context of a thread (tracing span, SLF4J MDC, security context, …), captured by
 * {@link ExecutorProvider#captureContext()} so that it can be restored in tasks that run later, possibly on another
 * thread.
 *
 * @since 1.23.0
 */
@Experimental
@FunctionalInterface
public interface CapturedContext {

    /**
     * Nothing captured: tasks run unchanged.
     */
    CapturedContext NONE = task -> task;

    /**
     * Wraps a task so that it runs with the captured context. This may be called for several tasks that run at the
     * same time, on any thread. The wrapped task must restore the previous context of the thread it runs on when it
     * completes, normally or exceptionally.
     *
     * @param task the task to wrap
     * @return the wrapped task
     */
    Runnable wrap(Runnable task);
}
