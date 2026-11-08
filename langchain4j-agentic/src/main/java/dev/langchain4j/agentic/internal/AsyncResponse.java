package dev.langchain4j.agentic.internal;

import dev.langchain4j.internal.DefaultExecutorProvider;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AsyncResponse<T> implements DelayedResponse<T> {

    private final CompletableFuture<T> futureResponse;
    private final AtomicBoolean failureReported = new AtomicBoolean();

    public AsyncResponse(Supplier<T> responseSupplier) {
        this.futureResponse =
                CompletableFuture.supplyAsync(responseSupplier, DefaultExecutorProvider.getDefaultExecutor());
    }

    /**
     * Passes this response to {@code onComplete} when it completes, successfully or not, on the thread completing
     * it, or immediately on the calling thread when it already completed.
     */
    public void onComplete(Consumer<AsyncResponse<T>> onComplete) {
        futureResponse.whenComplete((value, error) -> onComplete.accept(this));
    }

    @Override
    public boolean isDone() {
        return futureResponse.isDone();
    }

    public boolean isFailed() {
        return futureResponse.isCompletedExceptionally();
    }

    /**
     * Returns {@code true} only the first time it is called on a response that failed, so that a failure is reported
     * once, also when the response stays in a scope where several root calls end.
     */
    public boolean reportFailureOnce() {
        return isFailed() && failureReported.compareAndSet(false, true);
    }

    @Override
    public T blockingGet() {
        return DelayedResponse.join(futureResponse);
    }

    @Override
    public String toString() {
        return String.valueOf(isFailed() ? FAILED : result());
    }
}
