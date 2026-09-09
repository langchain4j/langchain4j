package dev.langchain4j.service;

import java.util.concurrent.CompletableFuture;

/**
 * A trivial single-value asynchronous type used in tests to exercise the
 * {@link dev.langchain4j.spi.services.CompletableFutureAdapter} SPI without depending on Reactor or Mutiny.
 */
public class FutureBox<T> {

    private final CompletableFuture<T> future;

    public FutureBox(CompletableFuture<T> future) {
        this.future = future;
    }

    public CompletableFuture<T> future() {
        return future;
    }
}
