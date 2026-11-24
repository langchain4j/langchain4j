package dev.langchain4j.data.media;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LimitedBodySubscriberTest {

    private static final int MAX_SIZE_IN_BYTES = 100_000;

    private final AtomicBoolean cancelled = new AtomicBoolean();

    private BodySubscriber<byte[]> subscribe(BodySubscriber<byte[]> subscriber) {
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {}

            @Override
            public void cancel() {
                cancelled.set(true);
            }
        });
        return subscriber;
    }

    private static BodySubscriber<byte[]> collecting(OptionalLong contentLength) {
        return LimitedBodySubscriber.collecting(
                MAX_SIZE_IN_BYTES, contentLength, () -> new IllegalStateException("too large"));
    }

    private static byte[] bytes(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }

    /** Sends the bytes in buffers of the given sizes, several buffers per call of onNext. */
    private static void send(BodySubscriber<byte[]> subscriber, byte[] bytes, int bufferSize) {
        List<ByteBuffer> buffers = new ArrayList<>();
        for (int offset = 0; offset < bytes.length; offset += bufferSize) {
            buffers.add(ByteBuffer.wrap(Arrays.copyOfRange(bytes, offset, Math.min(offset + bufferSize, bytes.length))));
            if (buffers.size() == 3) {
                subscriber.onNext(buffers);
                buffers = new ArrayList<>();
            }
        }
        if (!buffers.isEmpty()) {
            subscriber.onNext(buffers);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 8 * 1024, 8 * 1024 + 1, 50_000, MAX_SIZE_IN_BYTES})
    void should_collect_bodies_of_every_size_with_and_without_a_content_length(int size) {
        byte[] body = bytes(size);
        for (OptionalLong contentLength : List.of(OptionalLong.empty(), OptionalLong.of(size))) {
            BodySubscriber<byte[]> subscriber = subscribe(collecting(contentLength));

            send(subscriber, body, 1000);
            subscriber.onComplete();

            assertThat(subscriber.getBody().toCompletableFuture())
                    .succeedsWithin(1, SECONDS)
                    .isEqualTo(body);
        }
        assertThat(cancelled).isFalse();
    }

    @Test
    void should_collect_a_body_that_differs_from_its_content_length() {
        byte[] body = bytes(30_000);

        BodySubscriber<byte[]> longer = subscribe(collecting(OptionalLong.of(10_000)));
        send(longer, body, 4096);
        longer.onComplete();
        BodySubscriber<byte[]> shorter = subscribe(collecting(OptionalLong.of(50_000)));
        send(shorter, body, 4096);
        shorter.onComplete();
        BodySubscriber<byte[]> invalid = subscribe(collecting(OptionalLong.of(-1)));
        send(invalid, body, 4096);
        invalid.onComplete();

        assertThat(longer.getBody().toCompletableFuture()).succeedsWithin(1, SECONDS).isEqualTo(body);
        assertThat(shorter.getBody().toCompletableFuture()).succeedsWithin(1, SECONDS).isEqualTo(body);
        assertThat(invalid.getBody().toCompletableFuture()).succeedsWithin(1, SECONDS).isEqualTo(body);
    }

    @Test
    void should_fail_and_cancel_once_the_body_is_larger_than_the_maximum() {
        BodySubscriber<byte[]> subscriber = subscribe(collecting(OptionalLong.empty()));

        send(subscriber, bytes(MAX_SIZE_IN_BYTES + 1), 1000);

        assertThat(cancelled).isTrue();
        assertThat(subscriber.getBody().toCompletableFuture())
                .failsWithin(1, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isExactlyInstanceOf(IllegalStateException.class)
                .withMessage("too large");
    }

    @Test
    void should_not_read_the_body_when_discarding_or_failing() {
        BodySubscriber<byte[]> discarding = subscribe(LimitedBodySubscriber.discarding());
        assertThat(cancelled).isTrue();
        assertThat(discarding.getBody().toCompletableFuture()).isCompletedWithValue(null);

        cancelled.set(false);
        IllegalStateException failure = new IllegalStateException("declared too large");
        BodySubscriber<byte[]> failing = subscribe(LimitedBodySubscriber.failing(failure));
        assertThat(cancelled).isTrue();
        assertThat(failing.getBody().toCompletableFuture())
                .failsWithin(1, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isSameAs(failure);
    }
}
