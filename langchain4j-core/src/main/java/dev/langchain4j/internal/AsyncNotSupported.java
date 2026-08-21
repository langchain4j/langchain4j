package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.AsyncNotSupportedException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Factory for the "not implemented" result of an asynchronous / reactive SPI default method.
 * <p>
 * The async and reactive SPI methods (e.g. {@code ChatModel.doChatAsync}, {@code EmbeddingStore.searchAsync},
 * {@code StreamingChatModel.doChat(ChatRequest)}) ship a {@code default} body for providers that have not opted into
 * the non-blocking path. Rather than <b>throwing</b> {@link AsyncNotSupportedException} synchronously - which would
 * escape the method's own return-type contract, so whether it is safe depends on whether the call site happened to be
 * inside a future/publisher stage that launders the throw - the defaults signal the failure through their
 * <em>return type's</em> error channel: a failed {@link CompletableFuture} for future-returning methods, or an
 * immediately-failing {@link Flow.Publisher} for publisher-returning methods. The framework's offload-or-fail-loud
 * orchestration then observes it uniformly (via {@code exceptionallyCompose}/{@code onError}), regardless of context.
 * <p>
 * This does not change genuine implementations, nor the public entry points ({@code chatAsync}, {@code embedAsync},
 * the AI-Service dispatch), which still convert their own synchronous {@code validate(request)} failures.
 *
 * @since 1.19.0
 */
@Internal
public final class AsyncNotSupported {

    private AsyncNotSupported() {}

    /**
     * @return an already-failed future carrying an {@link AsyncNotSupportedException} whose message follows the
     *         convention {@code "<methodName>() is not implemented by <implementationType>"}.
     */
    public static <T> CompletableFuture<T> failedFuture(Class<?> implementationType, String methodName) {
        return failedFuture(methodName + "() is not implemented by " + implementationType.getName());
    }

    /**
     * @return an already-failed future carrying an {@link AsyncNotSupportedException} with the given message (for
     *         defaults that provide their own, more detailed guidance).
     */
    public static <T> CompletableFuture<T> failedFuture(String message) {
        return CompletableFuture.failedFuture(new AsyncNotSupportedException(message));
    }

    /**
     * @return a cold {@link Flow.Publisher} that, on subscribe, immediately signals {@code onError} with an
     *         {@link AsyncNotSupportedException} whose message follows the convention
     *         {@code "<methodName>() is not implemented by <implementationType>"}.
     */
    public static <T> Flow.Publisher<T> failingPublisher(Class<?> implementationType, String methodName) {
        return failingPublisher(methodName + "() is not implemented by " + implementationType.getName());
    }

    /**
     * @return a cold {@link Flow.Publisher} that, on subscribe, immediately signals {@code onError} with an
     *         {@link AsyncNotSupportedException} carrying the given message.
     */
    public static <T> Flow.Publisher<T> failingPublisher(String message) {
        AsyncNotSupportedException error = new AsyncNotSupportedException(message);
        return subscriber -> {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {}

                @Override
                public void cancel() {}
            });
            subscriber.onError(error);
        };
    }
}
