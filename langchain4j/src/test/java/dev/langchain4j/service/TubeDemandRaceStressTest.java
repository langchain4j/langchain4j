package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Hammers the AI Service publisher pipeline with bounded demand against a model that emits from another thread.
 * <p>
 * That combination is what broke under mutiny-zero 1.2.1: a {@code request(n)} racing an emission could be lost,
 * leaving the item buffered and the stream hung forever. It reproduced on this pipeline in roughly 1 subscription
 * in 1000 - often enough to make CI flaky, rarely enough that the Reactive Streams TCK verifications never caught
 * it (they subscribe a few hundred times per run and passed throughout). This test subscribes thousands of times
 * instead, so it separates a fixed dependency from a broken one in about a second.
 * <p>
 * It exists to guard the mutiny-zero upgrade: if a future version reintroduces the defect, this fails immediately
 * rather than surfacing as an unexplained CI hang.
 *
 * @see <a href="https://github.com/smallrye/smallrye-mutiny-zero/issues/365">smallrye-mutiny-zero#365</a>
 */
class TubeDemandRaceStressTest {

    private static final int ITERATIONS = Integer.getInteger("stress.iterations", 5_000);
    private static final int ITEMS = 3;
    // Generous on purpose: a lost demand never arrives, so this only decides how long a genuine hang
    // waits before it is reported. It must not be tight enough for a loaded CI runner to trip it.
    private static final long PER_ITEM_TIMEOUT_MILLIS = 10_000L;

    interface StringStreamer {
        Flow.Publisher<String> chat(String message);
    }

    interface EventStreamer {
        Flow.Publisher<AiServiceStreamingEvent> chat(String message);
    }

    @Test
    void string_publisher_never_loses_demand() throws Exception {
        StringStreamer assistant = AiServices.builder(StringStreamer.class)
                .streamingChatModel(TckFixedCountStreamingChatModel.emitting(ITEMS))
                .build();
        int stuck = hammer(() -> assistant.chat("hi"), ITEMS);
        System.out.println("[stress] Publisher<String>: iterations=" + ITERATIONS + " stuck=" + stuck);
        assertThat(stuck).as("subscriptions that never delivered a requested item").isZero();
    }

    @Test
    void event_publisher_never_loses_demand() throws Exception {
        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(TckFixedCountStreamingChatModel.emitting(ITEMS))
                .build();
        int stuck = hammer(() -> assistant.chat("hi"), ITEMS);
        System.out.println("[stress] Publisher<AiServiceStreamingEvent>: iterations=" + ITERATIONS + " stuck=" + stuck);
        assertThat(stuck).as("subscriptions that never delivered a requested item").isZero();
    }

    /** Requests one item at a time, so every request(n) races the emitting thread. */
    private static <T> int hammer(java.util.function.Supplier<Flow.Publisher<T>> publishers, int items)
            throws Exception {
        int stuck = 0;
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
            CountDownLatch[] arrived = new CountDownLatch[] {new CountDownLatch(1)};
            AtomicReference<CountDownLatch> nextItem = new AtomicReference<>(arrived[0]);

            publishers.get().subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription s) {
                    subscription.set(s);
                }

                @Override
                public void onNext(T item) {
                    nextItem.get().countDown();
                }

                @Override
                public void onError(Throwable throwable) {
                    nextItem.get().countDown();
                }

                @Override
                public void onComplete() {
                    nextItem.get().countDown();
                }
            });

            boolean lost = false;
            for (int i = 0; i < items; i++) {
                CountDownLatch latch = new CountDownLatch(1);
                nextItem.set(latch);
                subscription.get().request(1);
                if (!latch.await(PER_ITEM_TIMEOUT_MILLIS, MILLISECONDS)) {
                    lost = true;
                    break;
                }
            }
            if (lost) {
                stuck++;
            }
            subscription.get().cancel();
        }
        return stuck;
    }
}
