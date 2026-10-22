package dev.langchain4j.service;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decorates a {@link Flow.Subscriber} so that an action runs exactly once when the stream ends, whether it completes,
 * fails or is canceled by the subscriber. On completion or failure, the action runs before the end is signaled to the
 * delegate. On cancellation, it runs after the upstream subscription has been canceled, so that the events fired by
 * the cancellation are part of the invocation.
 */
final class InvocationEndingSubscriber<T> implements Flow.Subscriber<T> {

    private final Flow.Subscriber<? super T> delegate;
    private final Runnable onEnd;
    private final AtomicBoolean ended = new AtomicBoolean(false);

    InvocationEndingSubscriber(Flow.Subscriber<? super T> delegate, Runnable onEnd) {
        // Reactive Streams rule 1.9 requires a null subscriber to be rejected with a NullPointerException
        this.delegate = Objects.requireNonNull(delegate, "subscriber");
        this.onEnd = ensureNotNull(onEnd, "onEnd");
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.delegate.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                subscription.request(n);
            }

            @Override
            public void cancel() {
                try {
                    subscription.cancel();
                } finally {
                    end();
                }
            }
        });
    }

    @Override
    public void onNext(T item) {
        this.delegate.onNext(item);
    }

    @Override
    public void onError(Throwable throwable) {
        end();
        this.delegate.onError(throwable);
    }

    @Override
    public void onComplete() {
        end();
        this.delegate.onComplete();
    }

    private void end() {
        if (this.ended.compareAndSet(false, true)) {
            this.onEnd.run();
        }
    }
}
