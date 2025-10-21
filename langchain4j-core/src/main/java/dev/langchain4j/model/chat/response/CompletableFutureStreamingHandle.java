package dev.langchain4j.model.chat.response;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.concurrent.CompletableFuture;

public class CompletableFutureStreamingHandle implements StreamingHandle { // TODO name, remove?

    private final CompletableFuture<Void> completableFuture;
    private volatile boolean isCancelled;

    public CompletableFutureStreamingHandle(CompletableFuture<Void> completableFuture) {
        this.completableFuture = ensureNotNull(completableFuture, "completableFuture");
    }

    @Override
    public void cancel() {
        isCancelled = true;
        try {
            completableFuture.cancel(true); // TODO does not work?
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }
}
