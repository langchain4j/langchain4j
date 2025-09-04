package dev.langchain4j.agentic.internal;

import java.util.concurrent.CompletableFuture;

public record AsyncResponse<T>(CompletableFuture<T> futureResponse) {

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
