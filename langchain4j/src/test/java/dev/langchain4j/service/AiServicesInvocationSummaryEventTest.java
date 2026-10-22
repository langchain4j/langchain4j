package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceInvocationSummaryEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.listener.AiServiceInvocationSummaryListener;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Tests that an {@link AiServiceInvocationSummaryEvent} is fired exactly once for every way an AI Service invocation can end,
 * including the ones that don't fire an {@link AiServiceCompletedEvent} or {@link AiServiceErrorEvent}.
 */
class AiServicesInvocationSummaryEventTest {

    interface AsyncAssistant {
        CompletableFuture<String> chat(String message);
    }

    interface ReactiveAssistant {
        Flow.Publisher<AiServiceStreamingEvent> chat(String message);
    }

    interface StreamingAssistant {
        TokenStream chat(String message);
    }

    interface Assistant {
        String chat(String message);
    }

    interface InnerAssistant {
        String chat(String message);
    }

    private final List<AiServiceInvocationSummaryEvent> summaries = new CopyOnWriteArrayList<>();
    private final AiServiceInvocationSummaryListener invocationSummaryListener = summaries::add;

    @Test
    void canceledAsyncInvocationFiresInvocationSummaryEventWithTheEventsCollectedSoFar() {
        var modelCalled = new CountDownLatch(1);
        ChatModel neverRespondingModel = new ChatModel() {
            @Override
            public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
                modelCalled.countDown();
                return new CompletableFuture<>();
            }
        };

        var assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(neverRespondingModel)
                .registerListener(invocationSummaryListener)
                .build();

        var future = assistant.chat("Hi");
        awaitLatch(modelCalled);

        future.cancel(true);

        assertThatThrownBy(future::join).isInstanceOf(CancellationException.class);
        assertThat(summaries)
                .singleElement()
                .satisfies(summary -> assertThat(summary.events())
                        .<Class<?>>map(AiServiceEvent::eventClass)
                        .containsExactly(
                                AiServiceStartedEvent.class,
                                AiServiceRequestIssuedEvent.class,
                                AiServiceCancelledEvent.class));
    }

    @Test
    void completedAsyncInvocationFiresASingleInvocationSummaryEvent() {
        var assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(new ChatModel() {
                    @Override
                    public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
                        return CompletableFuture.completedFuture(ChatResponse.builder()
                                .aiMessage(AiMessage.from("Hello"))
                                .build());
                    }
                })
                .registerListener(invocationSummaryListener)
                .build();

        assertThat(assistant.chat("Hi").join()).isEqualTo("Hello");

        assertThat(summaries)
                .singleElement()
                .satisfies(summary -> assertThat(summary.events())
                        .<Class<?>>map(AiServiceEvent::eventClass)
                        .containsExactly(
                                AiServiceStartedEvent.class,
                                AiServiceRequestIssuedEvent.class,
                                AiServiceResponseReceivedEvent.class,
                                AiServiceCompletedEvent.class));
    }

    @Test
    void canceledReactiveInvocationFiresInvocationSummaryEventWithTheEventsCollectedSoFar() {
        var assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingEventChatModelMock.thatStreams(AiMessage.from("Hello")))
                .retrievalAugmentor(neverCompletingAugmentor())
                .registerListener(invocationSummaryListener)
                .build();

        var subscriber = new RecordingSubscriber();
        assistant.chat("Hi").subscribe(subscriber);

        subscriber.subscription.get().cancel();
        subscriber.subscription.get().cancel();

        assertThat(summaries)
                .singleElement()
                .satisfies(summary -> assertThat(summary.events())
                        .<Class<?>>map(AiServiceEvent::eventClass)
                        .containsExactly(AiServiceStartedEvent.class, AiServiceCancelledEvent.class));
    }

    @Test
    void reactiveInvocationCanceledFromWithinOnSubscribeFiresInvocationSummaryEvent() {
        var assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingEventChatModelMock.thatStreams(AiMessage.from("Hello")))
                .retrievalAugmentor(neverCompletingAugmentor())
                .registerListener(invocationSummaryListener)
                .build();

        assistant.chat("Hi").subscribe(new RecordingSubscriber() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.cancel();
            }
        });

        assertThat(summaries)
                .singleElement()
                .satisfies(summary -> assertThat(summary.events())
                        .<Class<?>>map(AiServiceEvent::eventClass)
                        .containsExactly(AiServiceStartedEvent.class, AiServiceCancelledEvent.class));
    }

    @Test
    void rejectedSecondReactiveSubscriptionDoesNotEndTheFirstSubscriptionsInvocationSummary() {
        var assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingEventChatModelMock.thatStreams(AiMessage.from("Hello")))
                .retrievalAugmentor(neverCompletingAugmentor())
                // With a chat memory, the publisher can only be subscribed to once
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .registerListener(invocationSummaryListener)
                .build();

        var publisher = assistant.chat("Hi");
        var first = new RecordingSubscriber();
        var second = new RecordingSubscriber();

        publisher.subscribe(first);
        publisher.subscribe(second);

        awaitLatch(second.done);
        assertThat(summaries).isEmpty();

        first.subscription.get().cancel();

        assertThat(summaries)
                .singleElement()
                .satisfies(summary -> assertThat(summary.events())
                        .<Class<?>>map(AiServiceEvent::eventClass)
                        .containsExactly(AiServiceStartedEvent.class, AiServiceCancelledEvent.class));
    }

    @Test
    void completedReactiveInvocationFiresASingleInvocationSummaryEvent() {
        var assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingEventChatModelMock.thatStreams(AiMessage.from("Hello")))
                .registerListener(invocationSummaryListener)
                .build();

        var subscriber = new RecordingSubscriber();
        assistant.chat("Hi").subscribe(subscriber);

        awaitLatch(subscriber.done);
        assertThat(subscriber.completed).isTrue();
        assertThat(summaries)
                .singleElement()
                .satisfies(summary -> assertThat(summary.events())
                        .<Class<?>>map(AiServiceEvent::eventClass)
                        .startsWith(AiServiceStartedEvent.class)
                        .endsWith(AiServiceCompletedEvent.class)
                        .doesNotContain(AiServiceErrorEvent.class));
    }

    @Test
    void canceledTokenStreamFiresInvocationSummaryEventWithTheEventsCollectedSoFar() {
        var handle = new TestStreamingHandle();
        StreamingChatModel model = new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                // Streams one token, then stops for good once canceled
                handler.onPartialResponse(new PartialResponse("Hel"), new PartialResponseContext(handle));
            }
        };

        var assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .registerListener(invocationSummaryListener)
                .build();

        assistant
                .chat("Hi")
                .onPartialResponseWithContext(
                        (partialResponse, context) -> context.streamingHandle().cancel())
                .ignoreErrors()
                .start();

        assertThat(handle.isCancelled()).isTrue();
        assertThat(summaries)
                .singleElement()
                .satisfies(summary -> assertThat(summary.events())
                        .<Class<?>>map(AiServiceEvent::eventClass)
                        .containsExactly(
                                AiServiceStartedEvent.class,
                                AiServiceRequestIssuedEvent.class,
                                AiServiceCancelledEvent.class));
    }

    @Test
    void tokenStreamWithAConfigurationErrorFiresAnInvocationSummaryEventEndingWithTheError() {
        var assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hello"))
                .registerListener(invocationSummaryListener)
                .build();

        // Neither onError nor ignoreErrors was configured, so start() fails
        var tokenStream = assistant.chat("Hi").onPartialResponse(partialResponse -> {});

        assertThatThrownBy(tokenStream::start).isInstanceOf(IllegalConfigurationException.class);
        assertThat(summaries)
                .singleElement()
                .satisfies(summary -> assertThat(summary.events())
                        .<Class<?>>map(AiServiceEvent::eventClass)
                        .containsExactly(AiServiceStartedEvent.class, AiServiceErrorEvent.class));

        // Starting it again once fixed is a new invocation with its own summary
        var completed = new CountDownLatch(1);
        tokenStream.onCompleteResponse(response -> completed.countDown()).ignoreErrors().start();
        awaitLatch(completed);

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(1).events())
                .<Class<?>>map(AiServiceEvent::eventClass)
                .startsWith(AiServiceStartedEvent.class)
                .endsWith(AiServiceCompletedEvent.class);
        assertThat(summaries.get(1).invocationContext().invocationId())
                .isNotEqualTo(summaries.get(0).invocationContext().invocationId());
    }

    @Test
    void failedSynchronousInvocationFiresASingleInvocationSummaryEvent() {
        var assistant = AiServices.builder(Assistant.class)
                .chatModel(new ChatModel() {
                    @Override
                    public ChatResponse doChat(ChatRequest chatRequest) {
                        throw new RuntimeException("LLM invocation failed");
                    }
                })
                .registerListener(invocationSummaryListener)
                .build();

        assertThatThrownBy(() -> assistant.chat("Hi")).hasMessage("LLM invocation failed");

        assertThat(summaries)
                .singleElement()
                .satisfies(summary -> assertThat(summary.events())
                        .<Class<?>>map(AiServiceEvent::eventClass)
                        .containsExactly(
                                AiServiceStartedEvent.class,
                                AiServiceRequestIssuedEvent.class,
                                AiServiceErrorEvent.class));
    }

    @Test
    void nestedInvocationFiresItsOwnInvocationSummaryEvent() {
        var inner = AiServices.builder(InnerAssistant.class)
                .chatModel(respondingWith("inner"))
                .registerListener(invocationSummaryListener)
                .build();

        // The outer AI Service calls the inner one while its own invocation is in progress
        var outer = AiServices.builder(Assistant.class)
                .chatModel(new ChatModel() {
                    @Override
                    public ChatResponse doChat(ChatRequest chatRequest) {
                        return ChatResponse.builder()
                                .aiMessage(AiMessage.from("outer: " + inner.chat("Hi")))
                                .build();
                    }
                })
                .registerListener(invocationSummaryListener)
                .build();

        assertThat(outer.chat("Hi")).isEqualTo("outer: inner");

        assertThat(summaries)
                .hasSize(2)
                .allSatisfy(summary -> assertThat(summary.events())
                        .allSatisfy(event -> assertThat(
                                        event.invocationContext().invocationId())
                                .isEqualTo(summary.invocationContext().invocationId()))
                        .<Class<?>>map(AiServiceEvent::eventClass)
                        .containsExactly(
                                AiServiceStartedEvent.class,
                                AiServiceRequestIssuedEvent.class,
                                AiServiceResponseReceivedEvent.class,
                                AiServiceCompletedEvent.class))
                .extracting(summary -> summary.invocationContext().interfaceName())
                // The inner invocation ends first
                .containsExactly(InnerAssistant.class.getName(), Assistant.class.getName());
    }

    private static ChatModel respondingWith(String response) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(response))
                        .build();
            }
        };
    }

    @Test
    void eachCanceledReactiveSubscriptionFiresItsOwnInvocationSummaryEvent() {
        var assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingEventChatModelMock.thatStreams(AiMessage.from("Hello")))
                .retrievalAugmentor(neverCompletingAugmentor())
                .registerListener(invocationSummaryListener)
                .build();

        var publisher = assistant.chat("Hi");
        var first = new RecordingSubscriber();
        var second = new RecordingSubscriber();
        publisher.subscribe(first);
        publisher.subscribe(second);

        first.subscription.get().cancel();
        second.subscription.get().cancel();

        assertThat(summaries)
                .hasSize(2)
                .allSatisfy(summary -> assertThat(summary.events())
                        .<Class<?>>map(AiServiceEvent::eventClass)
                        .containsExactly(AiServiceStartedEvent.class, AiServiceCancelledEvent.class))
                .extracting(summary -> summary.invocationContext().invocationId())
                .doesNotHaveDuplicates();
    }

    @Test
    void concurrentReactiveSubscriptionsFireSeparateInvocationSummaryEvents() {
        var assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hello"))
                .registerListener(invocationSummaryListener)
                .build();

        var publisher = assistant.chat("Hi");
        List<RecordingSubscriber> subscribers = IntStream.range(0, 8)
                .mapToObj(i -> new RecordingSubscriber())
                .toList();
        subscribers.parallelStream().forEach(publisher::subscribe);
        subscribers.forEach(subscriber -> awaitLatch(subscriber.done));

        assertThat(summaries)
                .hasSize(8)
                .allSatisfy(summary -> assertThat(summary.events())
                        .allSatisfy(event -> assertThat(event.invocationContext().invocationId())
                                .isEqualTo(summary.invocationContext().invocationId()))
                        .<Class<?>>map(AiServiceEvent::eventClass)
                        .startsWith(AiServiceStartedEvent.class)
                        .endsWith(AiServiceCompletedEvent.class)
                        .containsOnlyOnce(AiServiceStartedEvent.class))
                .extracting(summary -> summary.invocationContext().invocationId())
                .doesNotHaveDuplicates();
    }

    @Test
    void eachTokenStreamStartFiresItsOwnInvocationSummaryEvent() {
        var assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hello"))
                .registerListener(invocationSummaryListener)
                .build();

        var tokenStream = assistant.chat("Hi");
        var completed = new CountDownLatch(2);
        tokenStream.onCompleteResponse(response -> completed.countDown()).ignoreErrors();
        tokenStream.start();
        tokenStream.start();
        awaitLatch(completed);

        assertThat(summaries)
                .hasSize(2)
                .allSatisfy(summary -> assertThat(summary.events())
                        .<Class<?>>map(AiServiceEvent::eventClass)
                        .startsWith(AiServiceStartedEvent.class)
                        .endsWith(AiServiceCompletedEvent.class))
                .extracting(summary -> summary.invocationContext().invocationId())
                .doesNotHaveDuplicates();
    }

    // A RAG augmentor that never completes, so the invocation can be canceled while it's in flight
    private static RetrievalAugmentor neverCompletingAugmentor() {
        return new RetrievalAugmentor() {
            @Override
            public AugmentationResult augment(AugmentationRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<AugmentationResult> augmentAsync(AugmentationRequest request) {
                return new CompletableFuture<>();
            }
        };
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            assertThat(latch.await(1, TimeUnit.MINUTES)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static class TestStreamingHandle implements StreamingHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        @Override
        public void cancel() {
            this.cancelled.set(true);
        }

        @Override
        public boolean isCancelled() {
            return this.cancelled.get();
        }
    }

    private static class RecordingSubscriber implements Flow.Subscriber<AiServiceStreamingEvent> {
        private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        private final CountDownLatch done = new CountDownLatch(1);
        private volatile boolean completed;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription.set(subscription);
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(AiServiceStreamingEvent item) {}

        @Override
        public void onError(Throwable throwable) {
            this.done.countDown();
        }

        @Override
        public void onComplete() {
            this.completed = true;
            this.done.countDown();
        }
    }
}
