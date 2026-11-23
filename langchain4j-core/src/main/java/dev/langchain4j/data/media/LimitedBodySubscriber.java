package dev.langchain4j.data.media;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.util.List;
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

    private final int maxSizeInBytes;
    private final @Nullable Supplier<RuntimeException> tooLarge;
    private final @Nullable RuntimeException failure;
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private Flow.@Nullable Subscription subscription;

    private LimitedBodySubscriber(
            int maxSizeInBytes,
            @Nullable Supplier<RuntimeException> tooLarge,
            @Nullable RuntimeException failure) {
        this.maxSizeInBytes = maxSizeInBytes;
        this.tooLarge = tooLarge;
        this.failure = failure;
    }

    /**
     * Collects the body, or fails with the exception from {@code tooLarge} once it exceeds {@code maxSizeInBytes}.
     */
    static BodySubscriber<byte[]> collecting(int maxSizeInBytes, Supplier<RuntimeException> tooLarge) {
        return new LimitedBodySubscriber(maxSizeInBytes, tooLarge, null);
    }

    /**
     * Does not read the body at all; the body is {@code null}.
     */
    static BodySubscriber<byte[]> discarding() {
        return new LimitedBodySubscriber(0, null, null);
    }

    /**
     * Does not read the body at all and fails with the given exception.
     */
    static BodySubscriber<byte[]> failing(RuntimeException failure) {
        return new LimitedBodySubscriber(0, null, failure);
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
            if (buffer.size() + (long) byteBuffer.remaining() > maxSizeInBytes) {
                subscription.cancel();
                body.completeExceptionally(tooLarge.get());
                return;
            }
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            buffer.write(bytes, 0, bytes.length);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        body.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        body.complete(buffer.toByteArray());
    }

    @Override
    public CompletionStage<byte[]> getBody() {
        return body;
    }
}
