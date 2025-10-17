package dev.langchain4j.model.chat.response;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.concurrent.CompletableFuture;

public class CompletableFutureStreamingHandle implements StreamingHandle { // TODO name

    private final CompletableFuture<Void> completableFuture;

    public CompletableFutureStreamingHandle(CompletableFuture<Void> completableFuture) {
        this.completableFuture = ensureNotNull(completableFuture, "completableFuture");
    }

    @Override
    public void cancel() {
        completableFuture.cancel(true); // TODO does not work
    }

    @Override
    public boolean isCancelled() {
        return completableFuture.isCancelled(); // TODO
    }
}
