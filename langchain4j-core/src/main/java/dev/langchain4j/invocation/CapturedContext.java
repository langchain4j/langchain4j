package dev.langchain4j.invocation;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.spi.ExecutorProvider;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

/**
 * The context of the thread that started an AI Service invocation, captured with
 * {@link ExecutorProvider#captureContext()} and carried by the {@link InvocationContext} of the invocation, so that
 * every task the invocation offloads runs with it, whichever thread submits the task.
 */
@Internal
public final class CapturedContext {

    private final UnaryOperator<Runnable> contextRestorer;

    private CapturedContext(UnaryOperator<Runnable> contextRestorer) {
        this.contextRestorer = contextRestorer;
    }

    /**
     * Captures the context of the calling thread into the given builder, if an {@link ExecutorProvider} that
     * captures context is registered.
     *
     * @return the given builder
     */
    public static InvocationContext.Builder captureInto(InvocationContext.Builder builder) {
        UnaryOperator<Runnable> contextRestorer = DefaultExecutorProvider.captureContext();
        builder.capturedContext = contextRestorer == null ? null : new CapturedContext(contextRestorer);
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
        UnaryOperator<Runnable> contextRestorer = defaultInvocationContext.capturedContext().contextRestorer;
        return task -> executor.execute(contextRestorer.apply(task));
    }
}
