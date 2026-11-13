package dev.langchain4j.internal;

import dev.langchain4j.Internal;

import java.util.concurrent.Flow;

@Internal
public class InternalFlowUtils {

    public static final Flow.Subscription EMPTY_SUBSCRIPTION = new Flow.Subscription() {
        @Override
        public void request(long n) {}

        @Override
        public void cancel() {}
    };

    /**
     * A {@link Flow.Publisher} that fails every subscriber with the given error: it calls {@code onSubscribe} with
     * {@link #EMPTY_SUBSCRIPTION}, then {@code onError}.
     */
    public static <T> Flow.Publisher<T> failedPublisher(Throwable error) {
        return subscriber -> {
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
            subscriber.onError(error);
        };
    }

}
