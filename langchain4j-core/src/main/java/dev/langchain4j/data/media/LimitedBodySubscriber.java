package dev.langchain4j.data.media;

import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Collects a response body up to a maximum size. A body that grows larger fails, and the subscription is cancelled,
 * which makes the JDK HTTP client close the connection instead of reading the rest.
 */
final class LimitedBodySubscriber implements BodySubscriber<byte[]> {

    private static final int DEFAULT_INITIAL_CAPACITY = 8 * 1024;

    private final int maxSizeInBytes;
    private final @Nullable Supplier<RuntimeException> tooLarge;
    private final @Nullable RuntimeException failure;
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private byte[] buffer;
    private int size;
    private Flow.@Nullable Subscription subscription;

    private LimitedBodySubscriber(
            int maxSizeInBytes,
            int initialCapacity,
            @Nullable Supplier<RuntimeException> tooLarge,
            @Nullable RuntimeException failure) {
        this.maxSizeInBytes = maxSizeInBytes;
        this.buffer = new byte[initialCapacity];
        this.tooLarge = tooLarge;
        this.failure = failure;
    }

    /**
     * Collects the body, or fails with the exception from {@code tooLarge} once it exceeds {@code maxSizeInBytes}.
     * When the {@code Content-Length} of the response is known and not larger than {@code maxSizeInBytes}, the buffer
     * has exactly that size from the start, so that a body of that size is never copied.
     */
    static BodySubscriber<byte[]> collecting(
            int maxSizeInBytes, OptionalLong contentLength, Supplier<RuntimeException> tooLarge) {
        boolean contentLengthFits = contentLength.isPresent()
                && contentLength.getAsLong() >= 0
                && contentLength.getAsLong() <= maxSizeInBytes;
        int initialCapacity = contentLengthFits
                ? (int) contentLength.getAsLong()
                : Math.min(DEFAULT_INITIAL_CAPACITY, maxSizeInBytes);
        return new LimitedBodySubscriber(maxSizeInBytes, initialCapacity, tooLarge, null);
    }

    /**
     * Does not read the body at all; the body is {@code null}.
     */
    static BodySubscriber<byte[]> discarding() {
        return new LimitedBodySubscriber(0, 0, null, null);
    }

    /**
     * Does not read the body at all and fails with the given exception.
     */
    static BodySubscriber<byte[]> failing(RuntimeException failure) {
        return new LimitedBodySubscriber(0, 0, null, failure);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        if (failure != null) {
            subscription.cancel();
            body.completeExceptionally(failure);
        } else if (tooLarge == null) {
            subscription.cancel();
            body.complete(null);
        } else {
            subscription.request(Long.MAX_VALUE);
        }
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
        if (body.isDone()) {
            return;
        }
        for (ByteBuffer byteBuffer : buffers) {
            int remaining = byteBuffer.remaining();
            if ((long) size + remaining > maxSizeInBytes) {
                subscription.cancel();
                body.completeExceptionally(tooLarge.get());
                return;
            }
            if (size + remaining > buffer.length) {
                long newCapacity = Math.max(size + remaining, 2L * buffer.length);
                buffer = Arrays.copyOf(buffer, (int) Math.min(newCapacity, maxSizeInBytes));
            }
            byteBuffer.get(buffer, size, remaining);
            size += remaining;
        }
    }

    @Override
    public void onError(Throwable throwable) {
        body.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        body.complete(size == buffer.length ? buffer : Arrays.copyOf(buffer, size));
    }

    @Override
    public CompletionStage<byte[]> getBody() {
        return body;
    }
}
