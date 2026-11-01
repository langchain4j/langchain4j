package dev.langchain4j.service;

import java.util.concurrent.Flow;

/**
 * A trivial reactive container type used in tests to exercise the
 * {@link dev.langchain4j.spi.services.PublisherAdapter} SPI without depending on Reactor ({@code Flux}) or
 * Mutiny ({@code Multi}).
 */
public class ReactiveBox<T> {

    private final Flow.Publisher<T> publisher;

    public ReactiveBox(Flow.Publisher<T> publisher) {
        this.publisher = publisher;
    }

    public Flow.Publisher<T> publisher() {
        return publisher;
    }
}
