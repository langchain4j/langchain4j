package dev.langchain4j.internal;

import static dev.langchain4j.internal.Exceptions.restoreInterruptIfInterrupted;

import dev.langchain4j.Internal;

import java.util.concurrent.Flow;
import java.util.function.Supplier;

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

    /**
     * Calls an operation that returns a {@link Flow.Publisher} so that the caller never sees it throw, which is the
     * rule for every method that returns a {@code Publisher}: anything the operation throws, including an
     * {@link Error}, is returned as a {@link #failedPublisher(Throwable) failed publisher}, and a {@code null}
     * publisher as a publisher failed with an {@link IllegalStateException}. This is the {@code Publisher}
     * counterpart of {@link CompletableFutureUtils#failOnThrow(Supplier)}.
     * <p>
     * Use {@link #failPublisherOnThrow(Object, String, Supplier)} when the operation can return {@code null}, so that
     * the message names the method that did.
     *
     * @param operation the operation that returns the publisher
     * @return the publisher returned by the operation, or a failed publisher
     */
    public static <T> Flow.Publisher<T> failPublisherOnThrow(Supplier<Flow.Publisher<T>> operation) {
        return failPublisherOnThrow(operation, () -> "The reactive operation");
    }

    /**
     * Like {@link #failPublisherOnThrow(Supplier)}, but a {@code null} publisher fails with an
     * {@link IllegalStateException} that names the method that returned it: the class name of {@code target}, then
     * {@code method}, for example {@code "<class name of target>.doChat(ChatRequest) returned null"}.
     *
     * @param target    the object whose method the operation calls
     * @param method    the method the operation calls, for example {@code "doChat(ChatRequest)"}
     * @param operation the operation that returns the publisher
     * @return the publisher returned by the operation, or a failed publisher
     */
    public static <T> Flow.Publisher<T> failPublisherOnThrow(
            Object target, String method, Supplier<Flow.Publisher<T>> operation) {
        return failPublisherOnThrow(operation, () -> target.getClass().getName() + "." + method);
    }

    private static <T> Flow.Publisher<T> failPublisherOnThrow(
            Supplier<Flow.Publisher<T>> operation, Supplier<String> name) {
        Flow.Publisher<T> publisher;
        try {
            publisher = operation.get();
        } catch (Throwable error) {
            restoreInterruptIfInterrupted(error);
            return failedPublisher(error);
        }
        if (publisher == null) {
            return failedPublisher(new IllegalStateException(name.get() + " returned null"));
        }
        return publisher;
    }
}
