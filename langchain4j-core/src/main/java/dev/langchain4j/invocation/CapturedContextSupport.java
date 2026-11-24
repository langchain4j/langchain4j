package dev.langchain4j.invocation;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.spi.CapturedContext;
import dev.langchain4j.spi.ExecutorProvider;
import java.util.concurrent.Executor;

/**
 * Carries the context of the thread that started an AI Service invocation, captured with
 * {@link ExecutorProvider#captureContext()}, in the {@link InvocationContext} of the invocation, so that every task
 * the invocation offloads runs with it, whichever thread submits the task.
 */
@Internal
public final class CapturedContextSupport {

    private CapturedContextSupport() {}

    /**
     * Captures the context of the calling thread into the given builder, if an {@link ExecutorProvider} that
     * captures context is registered.
     *
     * @return the given builder
     */
    public static InvocationContext.Builder captureInto(InvocationContext.Builder builder) {
        builder.capturedContext = DefaultExecutorProvider.captureContext();
        return builder;
    }

    /**
     * @return an executor that submits tasks to the given executor, running them with the context captured when the
     *         given invocation started; the given executor itself if nothing was captured
     */
    public static Executor restoringIn(Executor executor, InvocationContext invocationContext) {
        if (executor == null
                || !(invocationContext instanceof DefaultInvocationContext defaultInvocationContext)
                || defaultInvocationContext.capturedContext() == null) {
            return executor;
        }
        CapturedContext capturedContext = defaultInvocationContext.capturedContext();
        return task -> executor.execute(capturedContext.wrap(task));
    }
}
