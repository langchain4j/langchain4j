package dev.langchain4j.model.bedrock;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;

/**
 * Verifies that cancelling the {@link Flow.Subscription} of {@link BedrockStreamingChatModel#chat(ChatRequest)}
 * <em>mid-stream</em> actually aborts the upstream stream — not merely that the publisher stops forwarding events.
 * Bedrock streams over the AWS SDK's {@code converseStream} event publisher, whose only abort mechanism is the
 * {@code StreamingHandle} (backed by the SDK {@link Subscription}) threaded through the streaming dispatch; the
 * publisher path captures that handle and cancels the SDK subscription when the downstream subscription is
 * cancelled.
 * <p>
 * A mocked {@link BedrockRuntimeAsyncClient} drives a fake {@link SdkPublisher} that dribbles content-delta events
 * one at a time and — crucially — records when its {@link Subscription#cancel()} is invoked. That observation is
 * what distinguishes a real abort from best-effort event-dropping (where the SDK subscription would keep running).
 * If cancellation were not honored, the SDK subscription would never be cancelled and this test would fail.
 */
class BedrockStreamingChatModelPublisherCancellationTest {

    private static final int TOTAL_CONTENT_CHUNKS = 50;
    private static final int EVENTS_BEFORE_CANCEL = 3;
    private static final long EMIT_DELAY_MS = 30;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean sdkSubscriptionCancelled = new AtomicBoolean();
    private final AtomicInteger eventsEmitted = new AtomicInteger();
    private final AtomicBoolean emittedWholeStream = new AtomicBoolean();

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void cancelling_subscription_mid_stream_cancels_the_sdk_subscription() throws Exception {
        List<ConverseStreamOutput> events = new ArrayList<>();
        for (int i = 0; i < TOTAL_CONTENT_CHUNKS; i++) {
            events.add(ContentBlockDeltaEvent.builder()
                    .contentBlockIndex(0)
                    .delta(ContentBlockDelta.fromText("chunk-" + i + " "))
                    .build());
        }

        BedrockRuntimeAsyncClient asyncClient = mock(BedrockRuntimeAsyncClient.class);
        when(asyncClient.converseStream(any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class)))
                .thenAnswer(invocation -> {
                    ConverseStreamResponseHandler responseHandler = invocation.getArgument(1);
                    responseHandler.onEventStream(new DribblingEventPublisher(events));
                    return new CompletableFuture<Void>(); // in-flight: never completes on its own
                });

        BedrockStreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId("us.amazon.nova-lite-v1:0")
                .client(asyncClient)
                .build();

        ChatRequest request =
                ChatRequest.builder().messages(UserMessage.from("hi")).build();

        AtomicInteger eventsBeforeCancel = new AtomicInteger();
        AtomicInteger eventsAfterCancel = new AtomicInteger();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<Flow.Subscription> subscriptionReference = new AtomicReference<>();
        CompletableFuture<Void> cancelledFuture = new CompletableFuture<>();
        CompletableFuture<Void> terminalSignal = new CompletableFuture<>();

        // when
        model.chat(request).subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriptionReference.set(subscription);
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingEvent event) {
                if (cancelled.get()) {
                    eventsAfterCancel.incrementAndGet(); // in-flight; the spec allows these (Rule 2.8)
                    return;
                }
                if (eventsBeforeCancel.incrementAndGet() == EVENTS_BEFORE_CANCEL) {
                    cancelled.set(true);
                    subscriptionReference.get().cancel();
                    cancelledFuture.complete(null);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                terminalSignal.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                terminalSignal.complete(null);
            }
        });

        // then: we received enough to cancel mid-stream...
        cancelledFuture.get(10, SECONDS);
        assertThat(eventsBeforeCancel).hasValue(EVENTS_BEFORE_CANCEL);

        // ...the cancellation propagated to the AWS SDK subscription...
        long deadline = System.nanoTime() + SECONDS.toNanos(10);
        while (!sdkSubscriptionCancelled.get() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(sdkSubscriptionCancelled)
                .as("AWS SDK subscription was never cancelled — the upstream stream was not aborted")
                .isTrue();

        // ...the stream stopped well short of the full response and never completed naturally.
        assertThat(emittedWholeStream)
                .as("the SDK publisher emitted the entire stream — cancellation did not stop it")
                .isFalse();
        assertThat(eventsEmitted.get()).isLessThan(TOTAL_CONTENT_CHUNKS);
        assertThat(terminalSignal)
                .as("pipeline ran to completion after cancel() instead of stopping")
                .isNotDone();
    }

    /**
     * A minimal {@link SdkPublisher} that emits the given events one per {@code request(1)} with a small delay, so a
     * downstream consumer can cancel mid-stream. It records whether {@link Subscription#cancel()} was invoked.
     */
    private final class DribblingEventPublisher implements SdkPublisher<ConverseStreamOutput> {

        private final List<ConverseStreamOutput> events;

        private DribblingEventPublisher(List<ConverseStreamOutput> events) {
            this.events = events;
        }

        @Override
        public void subscribe(Subscriber<? super ConverseStreamOutput> subscriber) {
            subscriber.onSubscribe(new Subscription() {

                private final AtomicInteger index = new AtomicInteger();

                @Override
                public void request(long n) {
                    // BedrockStreamingChatModel requests one element at a time; emit one per request, delayed.
                    scheduler.schedule(
                            () -> {
                                if (sdkSubscriptionCancelled.get()) {
                                    return;
                                }
                                int i = index.getAndIncrement();
                                if (i < events.size()) {
                                    eventsEmitted.incrementAndGet();
                                    subscriber.onNext(events.get(i));
                                } else {
                                    emittedWholeStream.set(true);
                                    subscriber.onComplete();
                                }
                            },
                            EMIT_DELAY_MS,
                            MILLISECONDS);
                }

                @Override
                public void cancel() {
                    sdkSubscriptionCancelled.set(true);
                }
            });
        }
    }
}
