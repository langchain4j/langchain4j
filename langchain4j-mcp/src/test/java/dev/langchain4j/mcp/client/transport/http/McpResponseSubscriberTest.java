package dev.langchain4j.mcp.client.transport.http;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.sse.HttpErrorResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class McpResponseSubscriberTest {

    private static final HttpStreamingEvent EVENT = new ServerSentEvent("message", "data");

    @Test
    void requests_one_event_at_a_time_and_preserves_completion_order() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        RecordingSubscriber downstream = new RecordingSubscriber();
        McpResponseSubscriber subscriber = new McpResponseSubscriber(downstream, tasks::add);
        RecordingSubscription upstream = new RecordingSubscription();
        subscriber.onSubscribe(upstream);

        assertThat(downstream.subscription).isNotNull();
        assertThat(upstream.requests).containsExactly(1L);
        subscriber.onNext(EVENT);
        assertThat(downstream.signals).isEmpty();
        assertThat(upstream.requests).containsExactly(1L);

        tasks.remove().run();
        assertThat(downstream.signals).containsExactly(EVENT);
        assertThat(upstream.requests).containsExactly(1L, 1L);

        subscriber.onNext(EVENT);
        subscriber.onComplete();
        assertThat(tasks).hasSize(1);
        tasks.remove().run();
        assertThat(downstream.signals).containsExactly(EVENT, EVENT, "complete");
        assertThat(upstream.requests).containsExactly(1L, 1L);
    }

    @Test
    void delivers_error_after_the_pending_event() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        RecordingSubscriber downstream = new RecordingSubscriber();
        McpResponseSubscriber subscriber = new McpResponseSubscriber(downstream, tasks::add);
        RecordingSubscription upstream = new RecordingSubscription();
        subscriber.onSubscribe(upstream);
        RuntimeException error = new RuntimeException("stream failed");

        subscriber.onNext(EVENT);
        subscriber.onError(error);
        tasks.remove().run();

        assertThat(downstream.signals).containsExactly(EVENT, error);
        assertThat(upstream.requests).containsExactly(1L);
    }

    @Test
    void error_head_cancels_upstream_before_queued_delivery_and_ignores_late_signals() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        RecordingSubscriber downstream = new RecordingSubscriber();
        McpResponseSubscriber subscriber = new McpResponseSubscriber(downstream, tasks::add);
        RecordingSubscription upstream = new RecordingSubscription();
        subscriber.onSubscribe(upstream);
        HttpErrorResponseReceived head = new HttpErrorResponseReceived(404, Map.of());

        subscriber.onNext(head);

        assertThat(upstream.cancelled).isTrue();
        assertThat(downstream.signals).isEmpty();
        assertThat(tasks).hasSize(1);
        assertThat(upstream.requests).containsExactly(1L);

        subscriber.onNext(EVENT);
        subscriber.onError(new RuntimeException("late error"));
        subscriber.onComplete();
        assertThat(tasks).hasSize(1);

        tasks.remove().run();
        assertThat(downstream.signals).containsExactly(head);
        assertThat(upstream.requests).containsExactly(1L);
    }

    @Test
    void direct_executor_does_not_request_after_error_head_when_delegate_keeps_subscription() {
        RecordingSubscriber downstream = new RecordingSubscriber();
        McpResponseSubscriber subscriber = new McpResponseSubscriber(downstream, Runnable::run);
        RecordingSubscription upstream = new RecordingSubscription();
        subscriber.onSubscribe(upstream);
        HttpErrorResponseReceived head = new HttpErrorResponseReceived(503, Map.of());

        subscriber.onNext(head);
        subscriber.onNext(EVENT);

        assertThat(downstream.signals).containsExactly(head);
        assertThat(upstream.cancelled).isTrue();
        assertThat(upstream.requests).containsExactly(1L);
    }

    @Test
    void rejected_error_head_delivery_fails_once_and_cancels_upstream() {
        RecordingSubscriber downstream = new RecordingSubscriber();
        RejectedExecutionException rejection = new RejectedExecutionException("executor stopped");
        McpResponseSubscriber subscriber = new McpResponseSubscriber(downstream, task -> {
            throw rejection;
        });
        RecordingSubscription upstream = new RecordingSubscription();
        subscriber.onSubscribe(upstream);

        subscriber.onNext(new HttpErrorResponseReceived(401, Map.of()));
        subscriber.onError(new RuntimeException("late error"));

        assertThat(upstream.cancelled).isTrue();
        assertThat(upstream.requests).containsExactly(1L);
        assertThat(downstream.signals).containsExactly(rejection);
    }

    @Test
    void cancellation_immediately_cancels_upstream_and_discards_queued_signals() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        RecordingSubscriber downstream = new RecordingSubscriber();
        McpResponseSubscriber subscriber = new McpResponseSubscriber(downstream, tasks::add);
        RecordingSubscription upstream = new RecordingSubscription();
        subscriber.onSubscribe(upstream);
        subscriber.onNext(EVENT);
        subscriber.onComplete();

        downstream.subscription.cancel();
        assertThat(upstream.cancelled).isTrue();
        tasks.remove().run();

        assertThat(downstream.signals).isEmpty();
        assertThat(upstream.requests).containsExactly(1L);
    }

    @Test
    void executor_rejection_cancels_upstream_and_reports_failure() {
        RecordingSubscriber downstream = new RecordingSubscriber();
        RejectedExecutionException rejection = new RejectedExecutionException("executor stopped");
        McpResponseSubscriber subscriber = new McpResponseSubscriber(downstream, task -> {
            throw rejection;
        });
        RecordingSubscription upstream = new RecordingSubscription();
        subscriber.onSubscribe(upstream);

        subscriber.onNext(EVENT);
        subscriber.onComplete();

        assertThat(upstream.cancelled).isTrue();
        assertThat(downstream.signals).containsExactly(rejection);
    }

    @Test
    void direct_executor_does_not_recurse_for_synchronous_publishers() throws Exception {
        verifySynchronousPublisher(Runnable::run);
    }

    @Test
    void callbacks_remain_serial_on_a_multithread_executor() throws Exception {
        var executor = Executors.newFixedThreadPool(4);
        try {
            verifySynchronousPublisher(executor);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void verifySynchronousPublisher(Executor executor) throws Exception {
        int eventCount = 10_000;
        AtomicInteger received = new AtomicInteger();
        AtomicInteger activeCallbacks = new AtomicInteger();
        CompletableFuture<Void> completed = new CompletableFuture<>();
        McpResponseSubscriber subscriber = new McpResponseSubscriber(
                new Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(HttpStreamingEvent item) {
                        if (activeCallbacks.incrementAndGet() != 1) {
                            completed.completeExceptionally(new IllegalStateException("Concurrent callbacks"));
                        }
                        received.incrementAndGet();
                        activeCallbacks.decrementAndGet();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        completed.completeExceptionally(throwable);
                    }

                    @Override
                    public void onComplete() {
                        if (activeCallbacks.get() != 0 || received.get() != eventCount) {
                            completed.completeExceptionally(new IllegalStateException("Completion overtook an event"));
                        } else {
                            completed.complete(null);
                        }
                    }
                },
                executor);
        subscriber.onSubscribe(new Flow.Subscription() {
            private int sent;

            @Override
            public void request(long count) {
                assertThat(count).isEqualTo(1);
                if (sent++ < eventCount) {
                    subscriber.onNext(EVENT);
                } else {
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {}
        });

        completed.get(5, TimeUnit.SECONDS);
        assertThat(received).hasValue(eventCount);
    }

    private static class RecordingSubscriber implements Flow.Subscriber<HttpStreamingEvent> {
        private final List<Object> signals = new ArrayList<>();
        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(HttpStreamingEvent item) {
            signals.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            signals.add(throwable);
        }

        @Override
        public void onComplete() {
            signals.add("complete");
        }
    }

    private static class RecordingSubscription implements Flow.Subscription {
        private final List<Long> requests = new ArrayList<>();
        private boolean cancelled;

        @Override
        public void request(long count) {
            requests.add(count);
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
