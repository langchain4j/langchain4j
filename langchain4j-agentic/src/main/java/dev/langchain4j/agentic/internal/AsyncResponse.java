package dev.langchain4j.agentic.internal;

import dev.langchain4j.internal.DefaultExecutorProvider;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AsyncResponse<T> implements DelayedResponse<T> {

    private final CompletableFuture<T> futureResponse;

    public AsyncResponse(Supplier<T> responseSupplier) {
        this(responseSupplier, null);
    }

    /**
     * Runs the supplier on the default executor and, when it completes, successfully or not, passes this response
     * to {@code onComplete} on that same thread.
     */
    public AsyncResponse(Supplier<T> responseSupplier, Consumer<AsyncResponse<T>> onComplete) {
        this.futureResponse =
                CompletableFuture.supplyAsync(responseSupplier, DefaultExecutorProvider.getDefaultExecutor());
        if (onComplete != null) {
            futureResponse.whenComplete((value, error) -> onComplete.accept(this));
        }
    }

    @Override
    public boolean isDone() {
        return futureResponse.isDone();
    }

    public boolean isFailed() {
        return futureResponse.isCompletedExceptionally();
    }

    @Override
    public T blockingGet() {
        return DelayedResponse.join(futureResponse);
    }

    @Override
    public String toString() {
        return result().toString();
    }
}
