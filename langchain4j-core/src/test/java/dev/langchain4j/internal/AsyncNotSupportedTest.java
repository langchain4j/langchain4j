package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.AsyncNotSupportedException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AsyncNotSupportedTest {

    @Test
    void failedFuture_carries_AsyncNotSupportedException_with_the_conventional_message() {
        CompletableFuture<String> future = AsyncNotSupported.failedFuture(getClass(), "searchAsync");

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isExactlyInstanceOf(AsyncNotSupportedException.class)
                .hasMessage("searchAsync() is not implemented by " + getClass().getName());
    }

    @Test
    void failedFuture_with_custom_message() {
        CompletableFuture<String> future = AsyncNotSupported.failedFuture("custom guidance");

        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isExactlyInstanceOf(AsyncNotSupportedException.class)
                .hasMessage("custom guidance");
    }

    @Test
    void failingPublisher_signals_onSubscribe_then_onError_with_AsyncNotSupportedException() {
        Flow.Publisher<String> publisher = AsyncNotSupported.failingPublisher(getClass(), "doChat");

        AtomicReference<Boolean> subscribed = new AtomicReference<>(false);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Boolean> completed = new AtomicReference<>(false);

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscribed.set(true);
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {}

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }
        });

        // Reactive Streams: onSubscribe must precede the terminal signal; the terminal signal is onError (not onComplete).
        assertThat(subscribed.get()).isTrue();
        assertThat(completed.get()).isFalse();
        assertThat(error.get())
                .isExactlyInstanceOf(AsyncNotSupportedException.class)
                .hasMessage("doChat() is not implemented by " + getClass().getName());
    }
}
