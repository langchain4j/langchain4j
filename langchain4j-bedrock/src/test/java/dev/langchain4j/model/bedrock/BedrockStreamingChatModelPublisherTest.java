package dev.langchain4j.model.bedrock;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.StreamingEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
 * Deterministic test of the reactive publisher path ({@link StreamingChatModel#chat(ChatRequest)} returning a
 * {@code Publisher<StreamingEvent>}) for Bedrock. A mocked {@link BedrockRuntimeAsyncClient} drives a fake
 * {@link SdkPublisher} that replays a canned {@code converseStream} event sequence, so no AWS credentials or
 * network access are needed; the test asserts the publisher emits the streamed text and a terminal complete event.
 */
class BedrockStreamingChatModelPublisherTest {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void should_stream_events_through_reactive_publisher() throws Exception {
        List<ConverseStreamOutput> events = List.of(
                MessageStartEvent.builder().role(ConversationRole.ASSISTANT).build(),
                textDelta("Hi"),
                textDelta(" there"),
                MessageStopEvent.builder().stopReason(StopReason.END_TURN).build(),
                ConverseStreamMetadataEvent.builder()
                        .usage(TokenUsage.builder()
                                .inputTokens(5)
                                .outputTokens(2)
                                .totalTokens(7)
                                .build())
                        .metrics(ConverseStreamMetrics.builder().latencyMs(42L).build())
                        .build());

        BedrockRuntimeAsyncClient asyncClient = mock(BedrockRuntimeAsyncClient.class);
        when(asyncClient.converseStream(any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class)))
                .thenAnswer(invocation -> {
                    ConverseStreamResponseHandler responseHandler = invocation.getArgument(1);
                    responseHandler.onEventStream(new ReplayingEventPublisher(events));
                    return new CompletableFuture<Void>();
                });

        StreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId("us.amazon.nova-lite-v1:0")
                .client(asyncClient)
                .build();

        ChatRequest request =
                ChatRequest.builder().messages(UserMessage.from("hi")).build();

        List<StreamingEvent> received = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> completed = new CompletableFuture<>();

        model.chat(request).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingEvent event) {
                received.add(event);
            }

            @Override
            public void onError(Throwable throwable) {
                completed.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                completed.complete(null);
            }
        });

        completed.get(10, SECONDS);

        // The concatenated partial-response text reproduces the streamed tokens...
        String streamedText = received.stream()
                .filter(event -> event instanceof PartialResponse)
                .map(event -> ((PartialResponse) event).text())
                .reduce("", String::concat);
        assertThat(streamedText).isEqualTo("Hi there");

        // ...and the terminal event carries the aggregated ChatResponse.
        StreamingEvent last = received.get(received.size() - 1);
        assertThat(last).isInstanceOf(CompleteResponse.class);
        assertThat(((CompleteResponse) last).chatResponse().aiMessage().text()).isEqualTo("Hi there");
    }

    private static ConverseStreamOutput textDelta(String text) {
        return ContentBlockDeltaEvent.builder()
                .contentBlockIndex(0)
                .delta(ContentBlockDelta.fromText(text))
                .build();
    }

    /**
     * A minimal {@link SdkPublisher} that replays the given events one per {@code request(1)}, mimicking the AWS
     * SDK's demand-driven event stream.
     */
    private final class ReplayingEventPublisher implements SdkPublisher<ConverseStreamOutput> {

        private final List<ConverseStreamOutput> events;

        private ReplayingEventPublisher(List<ConverseStreamOutput> events) {
            this.events = new ArrayList<>(events);
        }

        @Override
        public void subscribe(Subscriber<? super ConverseStreamOutput> subscriber) {
            subscriber.onSubscribe(new Subscription() {

                private final AtomicInteger index = new AtomicInteger();

                @Override
                public void request(long n) {
                    scheduler.schedule(
                            () -> {
                                int i = index.getAndIncrement();
                                if (i < events.size()) {
                                    subscriber.onNext(events.get(i));
                                } else {
                                    subscriber.onComplete();
                                }
                            },
                            5,
                            MILLISECONDS);
                }

                @Override
                public void cancel() {}
            });
        }
    }
}
