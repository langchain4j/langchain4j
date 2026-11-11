package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
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
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.AiServiceInvocationSummaryListener;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests that an {@link AiServiceInvocationSummaryEvent} is fired exactly once for every way an AI Service invocation can end,
 * including the ones that don't fire an {@link AiServiceCompletedEvent} or {@link AiServiceErrorEvent}, and that the
 * summary of a nested invocation is added to the summary of the invocation that called it.
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

    interface NestingAssistant {
        String chat(String message);

        CompletableFuture<String> chatAsync(String message);

        TokenStream chatStream(String message);

        String chatWithProvidedUserMessage();
    }

    private final List<AiServiceInvocationSummaryEvent> summaries = new CopyOnWriteArrayList<>();
    private final AiServiceInvocationSummaryListener invocationSummaryListener = summaries::add;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutDownExecutor() {
        executor.shutdownNow();
    }

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
                .streamingChatModel(MockStreamingChatModel.thatStreams("Hello"))
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
    void invocationCalledFromTheChatModelIsNested() {
        var inner = AiServices.builder(InnerAssistant.class)
                .chatModel(respondingWith("inner"))
                .registerListener(invocationSummaryListener)
                .build();

        // The outer AI Service calls the inner one from its ChatModel while its own invocation is in progress
        var outer = AiServices.builder(Assistant.class)
                .chatModel(MockChatModel.thatResponds(chatRequest -> AiMessage.from("outer: " + inner.chat("Hi"))).thenRepeats())
                .registerListener(invocationSummaryListener)
                .build();

        assertThat(outer.chat("Hi")).isEqualTo("outer: inner");

        // The inner invocation ends first
        assertThat(summaries)
                .extracting(summary -> summary.invocationContext().interfaceName())
                .containsExactly(InnerAssistant.class.getName(), Assistant.class.getName());
        var innerSummary = summaries.get(0);
        var outerSummary = summaries.get(1);
        assertThat(innerSummary.invocationContext().parentInvocationId())
                .isEqualTo(outerSummary.invocationContext().invocationId());
        assertThat(innerSummary.events())
                .<Class<?>>map(AiServiceEvent::eventClass)
                .containsExactly(
                        AiServiceStartedEvent.class,
                        AiServiceRequestIssuedEvent.class,
                        AiServiceResponseReceivedEvent.class,
                        AiServiceCompletedEvent.class);
        assertThat(outerSummary.events())
                .<Class<?>>map(AiServiceEvent::eventClass)
                .containsExactly(
                        AiServiceStartedEvent.class,
                        AiServiceRequestIssuedEvent.class,
                        AiServiceInvocationSummaryEvent.class,
                        AiServiceResponseReceivedEvent.class,
                        AiServiceCompletedEvent.class);
        assertThat(outerSummary.events())
                .filteredOn(AiServiceInvocationSummaryEvent.class::isInstance)
                .singleElement()
                .isSameAs(innerSummary);
    }

    enum Execution {
        SYNC,
        ASYNC,
        PARALLEL,
        STREAMING
    }

    @ParameterizedTest
    @EnumSource(Execution.class)
    void nestedInvocationSummaryIsAddedToTheOuterSummaryAndStillFiredOnItsOwn(Execution execution) throws Exception {
        var children = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        var parents = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        var bothChildrenStarted = new CountDownLatch(execution == Execution.PARALLEL ? 2 : 1);
        NestingAssistant child = AiServices.builder(NestingAssistant.class)
                .chatModel(MockChatModel.thatResponds(request -> {
                    bothChildrenStarted.countDown();
                    try {
                        assertThat(bothChildrenStarted.await(10, SECONDS)).isTrue();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    return AiMessage.from("child answer");
                }).thenRepeats())
                .registerListener((AiServiceInvocationSummaryListener) children::add)
                .build();
        String toolName = execution == Execution.ASYNC ? "nestedAsync" : "nested";
        List<ToolExecutionRequest> requests = execution == Execution.PARALLEL
                ? List.of(toolCall("1", toolName), toolCall("2", toolName))
                : List.of(toolCall("1", toolName));
        List<AiMessage> parentResponses = List.of(AiMessage.from(requests), AiMessage.from("parent answer"));
        var builder = AiServices.builder(NestingAssistant.class)
                .tools(new NestedTools(child))
                .registerListener((AiServiceInvocationSummaryListener) parents::add);
        if (execution == Execution.STREAMING) {
            builder.streamingChatModel(MockStreamingChatModel.thatStreams(parentResponses));
        } else {
            builder.chatModel(MockChatModel.thatResponds(parentResponses));
        }
        if (execution == Execution.ASYNC || execution == Execution.PARALLEL) {
            builder.executeToolsConcurrently(executor);
        }
        NestingAssistant parent = builder.build();

        String answer =
                switch (execution) {
                    case SYNC, PARALLEL -> parent.chat("question");
                    case ASYNC -> parent.chatAsync("question").get(10, SECONDS);
                    case STREAMING -> {
                        var response = new CompletableFuture<ChatResponse>();
                        parent.chatStream("question")
                                .onPartialResponse(partialResponse -> {})
                                .onCompleteResponse(response::complete)
                                .onError(response::completeExceptionally)
                                .start();
                        yield response.get(10, SECONDS).aiMessage().text();
                    }
                };

        assertThat(answer).isEqualTo("parent answer");
        assertThat(parents).hasSize(1);
        assertThat(children).hasSize(requests.size());
        assertThat(parents.get(0).invocationContext().parentInvocationId()).isNull();
        assertThat(children)
                .allSatisfy(summary -> assertThat(summary.invocationContext().parentInvocationId())
                        .isEqualTo(parents.get(0).invocationContext().invocationId()));
        assertThat(parents.get(0).events().stream()
                        .filter(AiServiceInvocationSummaryEvent.class::isInstance)
                        .map(AiServiceInvocationSummaryEvent.class::cast))
                .containsExactlyInAnyOrderElementsOf(children);
        if (execution == Execution.SYNC) {
            // The nested summary is added when the child ends, inside the tool call
            assertThat(parents.get(0).events())
                    .<Class<?>>map(AiServiceEvent::eventClass)
                    .containsExactly(
                            AiServiceStartedEvent.class,
                            AiServiceRequestIssuedEvent.class,
                            AiServiceResponseReceivedEvent.class,
                            AiServiceInvocationSummaryEvent.class,
                            ToolExecutedEvent.class,
                            AiServiceRequestIssuedEvent.class,
                            AiServiceResponseReceivedEvent.class,
                            AiServiceCompletedEvent.class);
        }

        // Occupy both workers so the check also covers each thread used by parallel tool execution.
        var bothWorkersReady = new CountDownLatch(2);
        Runnable independentCall = () -> {
            bothWorkersReady.countDown();
            try {
                assertThat(bothWorkersReady.await(10, SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            child.chat("independent question");
        };
        CompletableFuture.allOf(
                        CompletableFuture.runAsync(independentCall, executor),
                        CompletableFuture.runAsync(independentCall, executor))
                .get(10, SECONDS);
        assertThat(children.stream()
                        .filter(summary -> summary.invocationContext().parentInvocationId() == null))
                .hasSize(2);
    }

    @Test
    void nestedInvocationWithoutItsOwnSummaryListenerIsNested() {
        var parents = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        NestingAssistant child = AiServices.builder(NestingAssistant.class)
                .chatModel(MockChatModel.thatResponds("child answer"))
                .build();
        NestingAssistant parent = AiServices.builder(NestingAssistant.class)
                .chatModel(MockChatModel.thatResponds(
                        AiMessage.from(toolCall("1", "nested")), AiMessage.from("parent answer")))
                .tools(new NestedTools(child))
                .registerListener((AiServiceInvocationSummaryListener) parents::add)
                .build();

        assertThat(parent.chat("question")).isEqualTo("parent answer");

        assertThat(parents).hasSize(1);
        assertThat(parents.get(0).events())
                .filteredOn(AiServiceInvocationSummaryEvent.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(AiServiceInvocationSummaryEvent.class, nested -> {
                    assertThat(nested.invocationContext().parentInvocationId())
                            .isEqualTo(parents.get(0).invocationContext().invocationId());
                    assertThat(nested.events())
                            .<Class<?>>map(AiServiceEvent::eventClass)
                            .containsExactly(
                                    AiServiceStartedEvent.class,
                                    AiServiceRequestIssuedEvent.class,
                                    AiServiceResponseReceivedEvent.class,
                                    AiServiceCompletedEvent.class);
                });
    }

    @Test
    void invocationCalledFromTheChatMemoryStoreIsNested() {
        var children = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        var parents = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        NestingAssistant child = childAssistant(children);
        AtomicInteger childCalls = new AtomicInteger();
        NestingAssistant parent = AiServices.builder(NestingAssistant.class)
                .chatModel(MockChatModel.thatResponds("parent answer").thenRepeats())
                .chatMemory(MessageWindowChatMemory.builder()
                        .maxMessages(10)
                        .chatMemoryStore(new InMemoryChatMemoryStore() {

                            @Override
                            public void updateMessages(Object memoryId, List<ChatMessage> messages) {
                                childCalls.incrementAndGet();
                                child.chat("summarize the conversation");
                                super.updateMessages(memoryId, messages);
                            }
                        })
                        .build())
                .registerListener((AiServiceInvocationSummaryListener) parents::add)
                .build();

        assertThat(parent.chat("question")).isEqualTo("parent answer");

        assertThat(childCalls.get()).isPositive();
        assertChildrenNestedInParent(children, parents, childCalls.get());
    }

    enum PartRunBeforeTheStartedEvent {
        CHAT_MEMORY_PROVIDER,
        SYSTEM_MESSAGE_PROVIDER,
        USER_MESSAGE_PROVIDER,
        SYSTEM_MESSAGE_TRANSFORMER
    }

    @ParameterizedTest
    @EnumSource(PartRunBeforeTheStartedEvent.class)
    void invocationCalledBeforeTheStartedEventIsLinkedButNotNested(
            PartRunBeforeTheStartedEvent part) {
        // These parts run before the AiServiceStartedEvent, so the child ends before the parent is tracked
        var children = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        var parents = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        NestingAssistant child = childAssistant(children);
        var builder = AiServices.builder(NestingAssistant.class)
                .chatModel(MockChatModel.thatResponds("parent answer"))
                .registerListener((AiServiceInvocationSummaryListener) parents::add);
        Function<Object, String> callChild = ignored -> child.chat("prepare the request");
        switch (part) {
            case CHAT_MEMORY_PROVIDER ->
                builder.chatMemoryProvider(memoryId -> {
                    callChild.apply(memoryId);
                    return MessageWindowChatMemory.withMaxMessages(10);
                });
            case SYSTEM_MESSAGE_PROVIDER -> builder.systemMessageProvider(callChild);
            case USER_MESSAGE_PROVIDER -> builder.userMessageProvider(callChild);
            case SYSTEM_MESSAGE_TRANSFORMER ->
                builder.systemMessageProvider(memoryId -> "You are helpful").systemMessageTransformer(callChild::apply);
        }
        NestingAssistant parent = builder.build();

        String answer = part == PartRunBeforeTheStartedEvent.USER_MESSAGE_PROVIDER
                ? parent.chatWithProvidedUserMessage()
                : parent.chat("question");

        assertThat(answer).isEqualTo("parent answer");
        assertThat(parents).hasSize(1);
        assertThat(children).hasSize(1);
        assertThat(children.get(0).invocationContext().parentInvocationId())
                .isEqualTo(parents.get(0).invocationContext().invocationId());
        assertThat(parents.get(0).events()).noneMatch(AiServiceInvocationSummaryEvent.class::isInstance);
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
                .streamingChatModel(MockStreamingChatModel.thatStreams("Hello").thenRepeats())
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
                .streamingChatModel(MockStreamingChatModel.thatStreams("Hello").thenRepeats())
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
    private static NestingAssistant childAssistant(List<AiServiceInvocationSummaryEvent> children) {
        return AiServices.builder(NestingAssistant.class)
                .chatModel(MockChatModel.thatResponds("child answer").thenRepeats())
                .registerListener((AiServiceInvocationSummaryListener) children::add)
                .build();
    }

    private static void assertChildrenNestedInParent(
            List<AiServiceInvocationSummaryEvent> children,
            List<AiServiceInvocationSummaryEvent> parents,
            int expectedChildren) {
        assertThat(parents).hasSize(1);
        AiServiceInvocationSummaryEvent parentSummary = parents.get(0);
        assertThat(parentSummary.invocationContext().parentInvocationId()).isNull();
        assertThat(children)
                .hasSize(expectedChildren)
                .allSatisfy(summary -> assertThat(summary.invocationContext().parentInvocationId())
                        .isEqualTo(parentSummary.invocationContext().invocationId()));
        List<AiServiceEvent> parentEvents = parentSummary.events();
        assertThat(parentEvents.stream()
                        .filter(AiServiceInvocationSummaryEvent.class::isInstance)
                        .map(AiServiceInvocationSummaryEvent.class::cast))
                .containsExactlyInAnyOrderElementsOf(children);
        // The summary still starts with the parent's own started event and ends with its own terminal event
        assertThat(parentEvents.get(0)).isInstanceOf(AiServiceStartedEvent.class);
        assertThat(parentEvents.get(parentEvents.size() - 1)).isInstanceOf(AiServiceCompletedEvent.class);
        assertThat(parentEvents.get(0).invocationContext().invocationId())
                .isEqualTo(parentSummary.invocationContext().invocationId());
    }

    private static ToolExecutionRequest toolCall(String id, String name) {
        return ToolExecutionRequest.builder().id(id).name(name).arguments("{}").build();
    }

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

    static class NestedTools {
        private final NestingAssistant child;

        NestedTools(NestingAssistant child) {
            this.child = child;
        }

        @Tool
        String nested() {
            return child.chat("child question");
        }

        @Tool
        CompletableFuture<String> nestedAsync() {
            return child.chatAsync("child question");
        }
    }
}
