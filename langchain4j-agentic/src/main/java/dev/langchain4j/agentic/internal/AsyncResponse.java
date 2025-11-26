package dev.langchain4j.agentic.internal;

import dev.langchain4j.internal.DefaultExecutorProvider;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class AsyncResponse<T> {

    private final CompletableFuture<T> futureResponse;

    public AsyncResponse(Supplier<T> responseSupplier) {
        this.futureResponse = CompletableFuture.supplyAsync(responseSupplier, DefaultExecutorProvider.getDefaultExecutorService());
    }

    public T blockingGet() {
        return futureResponse.join();
    }

    @Override
    public String toString() {
        return result().toString();
    }

    public Object result() {
        return futureResponse.isDone() ? futureResponse.join() : "<pending>";
    }
}
