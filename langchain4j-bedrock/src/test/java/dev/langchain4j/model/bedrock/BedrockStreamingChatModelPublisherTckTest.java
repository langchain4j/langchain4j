package dev.langchain4j.model.bedrock;

import static dev.langchain4j.reactive.streaming.ReactiveStreamingTestSupport.TCK_PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS;
import static dev.langchain4j.reactive.streaming.ReactiveStreamingTestSupport.tckTestEnvironment;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static dev.langchain4j.reactive.streaming.ReactiveStreamingTestSupport.partialResponsesOnly;
import static org.reactivestreams.FlowAdapters.toPublisher;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.PartialResponse;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
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
 * Reactive Streams TCK for the Bedrock streaming publisher
 * ({@link BedrockStreamingChatModel#chat(ChatRequest)} over the AWS SDK's native async client). A lightweight
 * {@link Proxy}-based {@link BedrockRuntimeAsyncClient} drives a fake {@link SdkPublisher} that replays a canned
 * {@code converseStream} event sequence, so no AWS credentials or network are needed. (A Mockito mock is deliberately
 * avoided: its inline mock maker keeps every mock in a process-global registry, which would strongly retain the
 * publisher graph and fail TCK spec 313's reference-cleanup check.)
 * <p>
 * As with the Anthropic TCK, Bedrock relays its framing events (message start/stop, metadata) alongside the text
 * deltas, so the publisher under test is a thin {@link PartialResponse}-only view: the filter forwards the real
 * upstream subscription, so the model publisher's {@code onSubscribe} / demand / cancel / error / complete contract
 * is exercised end-to-end, while {@code n} text deltas yield exactly {@code n} elements.
 */
public class BedrockStreamingChatModelPublisherTckTest extends PublisherVerification<ChatModelStreamingEvent> {

    private static final long MAX_ELEMENTS = 64L;

    private static ScheduledExecutorService scheduler;

    public BedrockStreamingChatModelPublisherTckTest() {
        super(
                tckTestEnvironment(), TCK_PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS);
    }

    @BeforeClass
    public static void startScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "bedrock-tck-eventstream");
            t.setDaemon(true);
            return t;
        });
    }

    @AfterClass
    public static void stopScheduler() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    @Override
    public long maxElementsFromPublisher() {
        return MAX_ELEMENTS;
    }

    @Override
    public Publisher<ChatModelStreamingEvent> createPublisher(long elements) {
        List<ConverseStreamOutput> events = new ArrayList<>();
        events.add(MessageStartEvent.builder().role(ConversationRole.ASSISTANT).build());
        for (long i = 0; i < elements; i++) {
            events.add(textDelta("x"));
        }
        events.add(MessageStopEvent.builder().stopReason(StopReason.END_TURN).build());
        events.add(ConverseStreamMetadataEvent.builder()
                .usage(TokenUsage.builder().inputTokens(5).outputTokens(2).totalTokens(7).build())
                .metrics(ConverseStreamMetrics.builder().latencyMs(1L).build())
                .build());
        return toPublisher(partialResponsesOnly(newModel(clientReplaying(events)).chat(request())));
    }

    @Override
    public Publisher<ChatModelStreamingEvent> createFailedPublisher() {
        BedrockRuntimeAsyncClient asyncClient =
                client(handler -> CompletableFuture.failedFuture(new RuntimeException("boom")));
        return toPublisher(partialResponsesOnly(newModel(asyncClient).chat(request())));
    }

    private static BedrockRuntimeAsyncClient clientReplaying(List<ConverseStreamOutput> events) {
        return client(handler -> {
            handler.onEventStream(new ReplayingEventPublisher(events));
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * A minimal {@link BedrockRuntimeAsyncClient} backed by a {@link Proxy}: {@code converseStream(request, handler)}
     * delegates to {@code converse}, and everything else returns a harmless default.
     */
    private static BedrockRuntimeAsyncClient client(
            Function<ConverseStreamResponseHandler, CompletableFuture<Void>> converse) {
        return (BedrockRuntimeAsyncClient) Proxy.newProxyInstance(
                BedrockRuntimeAsyncClient.class.getClassLoader(),
                new Class<?>[] {BedrockRuntimeAsyncClient.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "converseStream":
                            if (args != null
                                    && args.length == 2
                                    && args[1] instanceof ConverseStreamResponseHandler handler) {
                                return converse.apply(handler);
                            }
                            return CompletableFuture.completedFuture(null);
                        case "serviceName":
                            return "bedrock";
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "toString":
                            return "BedrockTckProxyClient";
                        default:
                            return null; // close() and any other unused method
                    }
                });
    }

    private static StreamingChatModel newModel(BedrockRuntimeAsyncClient client) {
        return BedrockStreamingChatModel.builder()
                .modelId("us.amazon.nova-lite-v1:0")
                .client(client)
                .build();
    }

    private static ChatRequest request() {
        return ChatRequest.builder().messages(UserMessage.from("hi")).build();
    }

    private static ConverseStreamOutput textDelta(String text) {
        return ContentBlockDeltaEvent.builder()
                .contentBlockIndex(0)
                .delta(ContentBlockDelta.fromText(text))
                .build();
    }


    /** Replays the given events one per {@code request(1)}, mimicking the AWS SDK's demand-driven event stream. */
    private static final class ReplayingEventPublisher implements SdkPublisher<ConverseStreamOutput> {

        private final List<ConverseStreamOutput> events;

        private ReplayingEventPublisher(List<ConverseStreamOutput> events) {
            this.events = new ArrayList<>(events);
        }

        @Override
        public void subscribe(Subscriber<? super ConverseStreamOutput> subscriber) {
            subscriber.onSubscribe(new ReplaySubscription(subscriber, events));
        }
    }

    // Named (non-capturing) subscription so cancel() can null out the subscriber reference — mimicking the real AWS
    // SDK, whose cancel() drops the event-stream subscriber. Required for Reactive Streams TCK spec 313 (a cancelled
    // publisher must eventually drop all references to its subscriber).
    private static final class ReplaySubscription implements Subscription {

        private final List<ConverseStreamOutput> events;
        private final AtomicInteger index = new AtomicInteger();
        private volatile Subscriber<? super ConverseStreamOutput> subscriber;

        private ReplaySubscription(
                Subscriber<? super ConverseStreamOutput> subscriber, List<ConverseStreamOutput> events) {
            this.subscriber = subscriber;
            this.events = events;
        }

        @Override
        public void request(long n) {
            if (subscriber == null) {
                return;
            }
            scheduler.schedule(this::emitOne, 0, MILLISECONDS);
        }

        private void emitOne() {
            Subscriber<? super ConverseStreamOutput> s = subscriber;
            if (s == null) {
                return;
            }
            int i = index.getAndIncrement();
            if (i < events.size()) {
                s.onNext(events.get(i));
            } else {
                subscriber = null;
                s.onComplete();
            }
        }

        @Override
        public void cancel() {
            subscriber = null;
        }
    }
}
