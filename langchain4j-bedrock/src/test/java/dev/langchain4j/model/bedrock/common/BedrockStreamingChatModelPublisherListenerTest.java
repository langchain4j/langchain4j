package dev.langchain4j.model.bedrock.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.model.bedrock.BedrockStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelPublisherListenerTest;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

/**
 * Drives the common {@link AbstractStreamingChatModelPublisherListenerTest} for the Bedrock provider. Bedrock does
 * not stream over HTTP, so instead of WireMock a mocked {@link BedrockRuntimeAsyncClient} drives a fake
 * {@link SdkPublisher} that replays a canned {@code converseStream} event sequence (no AWS credentials or network).
 */
class BedrockStreamingChatModelPublisherListenerTest extends AbstractStreamingChatModelPublisherListenerTest {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Override
    protected StreamingChatModel createModel(ChatModelListener listener, StreamScenario scenario) {
        BedrockRuntimeAsyncClient asyncClient = mock(BedrockRuntimeAsyncClient.class);

        if (scenario.fail()) {
            when(asyncClient.converseStream(
                            any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class)))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));
        } else {
            long perEventDelayMs = scenario.spreadOverMillis() > 0
                    ? Math.max(1, scenario.spreadOverMillis() / (scenario.contentChunks() + 4))
                    : 2;
            List<ConverseStreamOutput> events = successEvents(scenario.contentChunks());
            when(asyncClient.converseStream(
                            any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class)))
                    .thenAnswer(invocation -> {
                        ConverseStreamResponseHandler responseHandler = invocation.getArgument(1);
                        responseHandler.onEventStream(new DribblingEventPublisher(events, perEventDelayMs));
                        return new CompletableFuture<Void>();
                    });
        }

        return BedrockStreamingChatModel.builder()
                .modelId("us.amazon.nova-lite-v1:0")
                .client(asyncClient)
                .listeners(List.of(listener))
                .build();
    }

    private static List<ConverseStreamOutput> successEvents(int contentChunks) {
        List<ConverseStreamOutput> events = new ArrayList<>();
        events.add(MessageStartEvent.builder().role(ConversationRole.ASSISTANT).build());
        for (int i = 0; i < contentChunks; i++) {
            events.add(ContentBlockDeltaEvent.builder()
                    .contentBlockIndex(0)
                    .delta(ContentBlockDelta.fromText("chunk-" + i + " "))
                    .build());
        }
        events.add(MessageStopEvent.builder().stopReason(StopReason.END_TURN).build());
        events.add(ConverseStreamMetadataEvent.builder()
                .usage(TokenUsage.builder()
                        .inputTokens(5)
                        .outputTokens(contentChunks)
                        .totalTokens(5 + contentChunks)
                        .build())
                .metrics(ConverseStreamMetrics.builder().latencyMs(42L).build())
                .build());
        return events;
    }

    /**
     * Emits the given events one per {@code request(1)} with a per-event delay (mimicking the AWS SDK's
     * demand-driven event stream), and stops once the subscription is cancelled.
     */
    private final class DribblingEventPublisher implements SdkPublisher<ConverseStreamOutput> {

        private final List<ConverseStreamOutput> events;
        private final long perEventDelayMs;

        private DribblingEventPublisher(List<ConverseStreamOutput> events, long perEventDelayMs) {
            this.events = events;
            this.perEventDelayMs = perEventDelayMs;
        }

        @Override
        public void subscribe(Subscriber<? super ConverseStreamOutput> subscriber) {
            AtomicBoolean cancelled = new AtomicBoolean();
            subscriber.onSubscribe(new Subscription() {

                private final AtomicInteger index = new AtomicInteger();

                @Override
                public void request(long n) {
                    scheduler.schedule(
                            () -> {
                                if (cancelled.get()) {
                                    return;
                                }
                                int i = index.getAndIncrement();
                                if (i < events.size()) {
                                    subscriber.onNext(events.get(i));
                                } else {
                                    subscriber.onComplete();
                                }
                            },
                            perEventDelayMs,
                            TimeUnit.MILLISECONDS);
                }

                @Override
                public void cancel() {
                    cancelled.set(true);
                }
            });
        }
    }
}
