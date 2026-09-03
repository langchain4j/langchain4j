package dev.langchain4j.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DemandDecouplingPublisherTest {

    @Test
    void should_request_unbounded_upstream_regardless_of_downstream_demand() {
        AtomicLong upstreamRequest = new AtomicLong();
        Flow.Publisher<String> upstream = subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                upstreamRequest.addAndGet(n);
            }

            @Override
            public void cancel() {}
        });

        new DemandDecouplingPublisher<>(upstream, 8).subscribe(new CollectingSubscriber<>(1));

        assertThat(upstreamRequest).hasValue(Long.MAX_VALUE);
    }

    @Test
    void should_emit_only_what_the_subscriber_requested() {
        CollectingSubscriber<String> subscriber = new CollectingSubscriber<>(2);
        SourceSubscriber<String> source = subscribe(subscriber, 8);

        source.onNext("a");
        source.onNext("b");
        source.onNext("c");

        assertThat(subscriber.items).containsExactly("a", "b");
        assertThat(subscriber.completed).isFalse();

        subscriber.subscription.request(1);
        assertThat(subscriber.items).containsExactly("a", "b", "c");
    }

    @Test
    void should_deliver_buffered_items_before_completing() {
        CollectingSubscriber<String> subscriber = new CollectingSubscriber<>(0);
        SourceSubscriber<String> source = subscribe(subscriber, 8);

        source.onNext("a");
        source.onComplete();

        assertThat(subscriber.items).isEmpty();
        assertThat(subscriber.completed)
                .as("buffered items must be delivered before onComplete")
                .isFalse();

        subscriber.subscription.request(1);
        assertThat(subscriber.items).containsExactly("a");
        assertThat(subscriber.completed).isTrue();
    }

    @Test
    void should_fail_the_subscriber_and_cancel_upstream_on_buffer_overflow() {
        CollectingSubscriber<String> subscriber = new CollectingSubscriber<>(0);
        SourceSubscriber<String> source = subscribe(subscriber, 2);

        source.onNext("a");
        source.onNext("b");
        source.onNext("c"); // one too many

        assertThat(source.cancelled).isTrue();
        assertThat(subscriber.error).isInstanceOf(IllegalStateException.class).hasMessageContaining("Buffer overflow");
    }

    @Test
    void should_signal_illegal_argument_exception_on_non_positive_request() {
        CollectingSubscriber<String> subscriber = new CollectingSubscriber<>(0);
        SourceSubscriber<String> source = subscribe(subscriber, 8);

        subscriber.subscription.request(0);

        assertThat(subscriber.error).isInstanceOf(IllegalArgumentException.class);
        assertThat(source.cancelled).isTrue();
    }

    @Test
    void should_cancel_upstream_and_stop_emitting_when_the_subscriber_cancels() {
        CollectingSubscriber<String> subscriber = new CollectingSubscriber<>(Long.MAX_VALUE);
        SourceSubscriber<String> source = subscribe(subscriber, 8);

        source.onNext("a");
        subscriber.subscription.cancel();
        source.onNext("b");
        source.onComplete();

        assertThat(source.cancelled).isTrue();
        assertThat(subscriber.items).containsExactly("a");
        assertThat(subscriber.completed).isFalse();
    }

    @Test
    void should_reject_a_null_subscriber_with_a_npe() {
        Flow.Publisher<String> publisher = new DemandDecouplingPublisher<>(subscriber -> {}, 8);
        assertThatThrownBy(() -> publisher.subscribe(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void should_deliver_every_item_when_demand_races_a_producer_on_another_thread() throws Exception {
        int items = 10_000;
        for (int attempt = 0; attempt < 20; attempt++) {
            AtomicReference<Flow.Subscription> subscriptionRef = new AtomicReference<>();
            CountDownLatch subscribed = new CountDownLatch(1);
            List<Integer> received = new ArrayList<>();
            CountDownLatch done = new CountDownLatch(1);

            Flow.Publisher<Integer> source = subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {}

                @Override
                public void cancel() {}
            });
            AtomicReference<Flow.Subscriber<? super Integer>> sourceSubscriber = new AtomicReference<>();
            Flow.Publisher<Integer> capturing = subscriber -> {
                sourceSubscriber.set(subscriber);
                source.subscribe(subscriber);
            };

            new DemandDecouplingPublisher<>(capturing, items + 1).subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscriptionRef.set(subscription);
                    subscribed.countDown();
                }

                @Override
                public void onNext(Integer item) {
                    received.add(item);
                    subscriptionRef.get().request(1); // request from within onNext, racing the producer
                }

                @Override
                public void onError(Throwable throwable) {
                    done.countDown();
                }

                @Override
                public void onComplete() {
                    done.countDown();
                }
            });

            assertThat(subscribed.await(5, SECONDS)).isTrue();
            Thread producer = new Thread(() -> {
                for (int i = 0; i < items; i++) {
                    sourceSubscriber.get().onNext(i);
                }
                sourceSubscriber.get().onComplete();
            });
            producer.start();
            subscriptionRef.get().request(1);

            producer.join(SECONDS.toMillis(10));
            assertThat(done.await(10, SECONDS))
                    .as("the stream must terminate, attempt %s", attempt)
                    .isTrue();
            assertThat(received)
                    .as("every item must be delivered, attempt %s", attempt)
                    .hasSize(items);
        }
    }

    private static <T> SourceSubscriber<T> subscribe(CollectingSubscriber<T> subscriber, int bufferSize) {
        SourceSubscriber<T> source = new SourceSubscriber<>();
        new DemandDecouplingPublisher<T>(source::attach, bufferSize).subscribe(subscriber);
        return source;
    }

    /** Stands in for the wrapped publisher: lets the test push signals and observe cancellation. */
    private static class SourceSubscriber<T> {

        private Flow.Subscriber<? super T> subscriber;
        private boolean cancelled;

        void attach(Flow.Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {}

                @Override
                public void cancel() {
                    cancelled = true;
                }
            });
        }

        void onNext(T item) {
            subscriber.onNext(item);
        }

        void onComplete() {
            subscriber.onComplete();
        }
    }

    private static class CollectingSubscriber<T> implements Flow.Subscriber<T> {

        private final long initialRequest;
        private final List<T> items = new ArrayList<>();
        private final AtomicBoolean terminated = new AtomicBoolean();

        private Flow.Subscription subscription;
        private Throwable error;
        private boolean completed;

        CollectingSubscriber(long initialRequest) {
            this.initialRequest = initialRequest;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (initialRequest > 0) {
                subscription.request(initialRequest);
            }
        }

        @Override
        public void onNext(T item) {
            items.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            assertThat(terminated.compareAndSet(false, true))
                    .as("only one terminal signal")
                    .isTrue();
            this.error = throwable;
        }

        @Override
        public void onComplete() {
            assertThat(terminated.compareAndSet(false, true))
                    .as("only one terminal signal")
                    .isTrue();
            this.completed = true;
        }
    }
}
