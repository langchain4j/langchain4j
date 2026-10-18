package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.observability.api.listener.AiServiceCompletedListener;
import dev.langchain4j.observability.api.listener.AiServiceRequestIssuedListener;
import dev.langchain4j.observability.api.listener.AiServiceResponseReceivedListener;
import dev.langchain4j.observability.api.listener.AiServiceStartedListener;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AiServiceReactiveInvocationContextTest {

    interface EventStreamer {
        Flow.Publisher<AiServiceStreamingEvent> chat(@UserMessage String message, InvocationParameters parameters);
    }

    interface BoxStringStreamer {
        ReactiveBox<String> chat(String message);
    }

    @Test
    void each_subscription_has_its_own_invocation_id_throughout_the_tool_loop() throws Exception {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("temperature")
                .arguments("{}")
                .build();
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from(toolRequest), AiMessage.from("42"),
                AiMessage.from(toolRequest), AiMessage.from("42"),
                AiMessage.from(toolRequest), AiMessage.from("42"));
        AtomicReference<InvocationContext> originalContext = new AtomicReference<>();
        List<InvocationContext> listenerContexts = new CopyOnWriteArrayList<>();
        List<InvocationContext> toolContexts = new CopyOnWriteArrayList<>();
        List<InvocationContext> inputContexts = new CopyOnWriteArrayList<>();
        RetrievalAugmentor augmentor = new RetrievalAugmentor() {
            @Override
            public AugmentationResult augment(AugmentationRequest request) {
                inputContexts.add(request.metadata().invocationContext());
                return new AugmentationResult(request.chatMessage(), List.of());
            }

            @Override
            public CompletableFuture<AugmentationResult> augmentAsync(AugmentationRequest request) {
                return CompletableFuture.completedFuture(augment(request));
            }
        };
        ToolExecutor toolExecutor = new ToolExecutor() {
            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                return "42";
            }

            @Override
            public CompletableFuture<ToolExecutionResult> executeAsync(
                    ToolExecutionRequest request, InvocationContext context) {
                toolContexts.add(context);
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.builder().resultText("42").build());
            }
        };
        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .systemMessageProviderWithContext(context -> {
                    originalContext.set(context);
                    return "Answer the question";
                })
                .retrievalAugmentor(augmentor)
                .inputGuardrails(new InputGuardrail() {
                    @Override
                    public CompletableFuture<InputGuardrailResult> validateAsync(InputGuardrailRequest request) {
                        inputContexts.add(request.requestParams().invocationContext());
                        return CompletableFuture.completedFuture(success());
                    }
                })
                .toolProvider(request -> ToolProviderResult.builder()
                        .add(ToolSpecification.builder().name("temperature").build(), toolExecutor)
                        .build())
                .registerListeners(
                        (AiServiceStartedListener) event -> listenerContexts.add(event.invocationContext()),
                        (AiServiceRequestIssuedListener) event -> listenerContexts.add(event.invocationContext()),
                        (AiServiceResponseReceivedListener) event -> listenerContexts.add(event.invocationContext()),
                        (ToolExecutedEventListener) event -> listenerContexts.add(event.invocationContext()),
                        (AiServiceCompletedListener) event -> listenerContexts.add(event.invocationContext()))
                .build();
        InvocationParameters parameters = InvocationParameters.from("custom", "value");
        Flow.Publisher<AiServiceStreamingEvent> publisher = assistant.chat("What is the temperature?", parameters);
        List<UUID> invocationIds = new ArrayList<>();

        assertThat(listenerContexts).isEmpty();
        for (int i = 0; i < 3; i++) {
            List<AiServiceStreamingEvent> events = collect(publisher).get(10, TimeUnit.SECONDS);
            assertThat(events).isNotEmpty();
            UUID invocationId = events.get(0).invocationContext().invocationId();
            invocationIds.add(invocationId);
            assertThat(events)
                    .allSatisfy(event ->
                            assertThat(event.invocationContext().invocationId()).isEqualTo(invocationId));
            assertThat(listenerContexts)
                    .hasSize(7)
                    .allSatisfy(context -> assertThat(context.invocationId()).isEqualTo(invocationId));
            assertThat(inputContexts)
                    .hasSize(2)
                    .allSatisfy(context -> assertThat(context.invocationId()).isEqualTo(invocationId));
            assertThat(toolContexts).singleElement().satisfies(context -> {
                assertThat(context.invocationId()).isEqualTo(invocationId);
                assertThat(context.interfaceName())
                        .isEqualTo(originalContext.get().interfaceName());
                assertThat(context.methodName()).isEqualTo("chat");
                assertThat(context.methodArguments()).containsExactly("What is the temperature?", parameters);
                assertThat(context.chatMemoryId())
                        .isEqualTo(originalContext.get().chatMemoryId());
                assertThat(context.invocationParameters()).isSameAs(parameters);
                assertThat(context.timestamp()).isEqualTo(originalContext.get().timestamp());
            });
            listenerContexts.clear();
            toolContexts.clear();
            inputContexts.clear();
        }

        assertThat(invocationIds).doesNotHaveDuplicates();
        assertThat(invocationIds.get(0)).isEqualTo(originalContext.get().invocationId());
        assertThat(model.requests()).hasSize(6);
    }

    @Test
    void concurrent_subscriptions_have_distinct_invocation_ids() throws Exception {
        int subscriptionCount = 8;
        Queue<Runnable> emissions = new ConcurrentLinkedQueue<>();
        StreamingEventChatModelMock model = new StreamingEventChatModelMock(
                Collections.nCopies(subscriptionCount, AiMessage.from("Hello")), 0, emissions::add);
        AtomicReference<InvocationContext> originalContext = new AtomicReference<>();
        List<UUID> startedIds = new CopyOnWriteArrayList<>();
        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .systemMessageProviderWithContext(context -> {
                    originalContext.set(context);
                    return "Answer the question";
                })
                .registerListener((AiServiceStartedListener)
                        event -> startedIds.add(event.invocationContext().invocationId()))
                .build();
        Flow.Publisher<AiServiceStreamingEvent> publisher = assistant.chat("Hi", new InvocationParameters());
        CyclicBarrier start = new CyclicBarrier(subscriptionCount);
        CountDownLatch subscribed = new CountDownLatch(subscriptionCount);
        ExecutorService executor = Executors.newFixedThreadPool(subscriptionCount);
        try {
            List<Future<List<AiServiceStreamingEvent>>> results = new ArrayList<>();
            for (int i = 0; i < subscriptionCount; i++) {
                results.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    CompletableFuture<List<AiServiceStreamingEvent>> result = collect(publisher);
                    subscribed.countDown();
                    return result.get(10, TimeUnit.SECONDS);
                }));
            }
            assertThat(subscribed.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(emissions).hasSize(subscriptionCount);
            // Hold every model response until all subscriptions are active.
            emissions.forEach(Runnable::run);

            List<UUID> invocationIds = new ArrayList<>();
            for (Future<List<AiServiceStreamingEvent>> result : results) {
                List<AiServiceStreamingEvent> events = result.get(10, TimeUnit.SECONDS);
                assertThat(events).isNotEmpty();
                UUID invocationId = events.get(0).invocationContext().invocationId();
                invocationIds.add(invocationId);
                assertThat(events)
                        .allSatisfy(
                                event -> assertThat(event.invocationContext().invocationId())
                                        .isEqualTo(invocationId));
            }
            assertThat(invocationIds)
                    .doesNotHaveDuplicates()
                    .contains(originalContext.get().invocationId());
            assertThat(startedIds).containsExactlyInAnyOrderElementsOf(invocationIds);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void adapted_text_stream_uses_a_fresh_invocation_id_for_each_subscription() throws Exception {
        List<UUID> startedIds = new CopyOnWriteArrayList<>();
        List<UUID> completedIds = new CopyOnWriteArrayList<>();
        BoxStringStreamer assistant = AiServices.builder(BoxStringStreamer.class)
                .streamingChatModel(StreamingEventChatModelMock.thatStreams(
                        AiMessage.from("Hello"), AiMessage.from("Hello"), AiMessage.from("Hello")))
                .registerListeners(
                        (AiServiceStartedListener) event ->
                                startedIds.add(event.invocationContext().invocationId()),
                        (AiServiceCompletedListener) event ->
                                completedIds.add(event.invocationContext().invocationId()))
                .build();
        ReactiveBox<String> stream = assistant.chat("Hi");

        for (int i = 0; i < 3; i++) {
            assertThat(String.join("", collect(stream.publisher()).get(10, TimeUnit.SECONDS)))
                    .isEqualTo("Hello");
        }

        assertThat(startedIds).hasSize(3).doesNotHaveDuplicates();
        assertThat(completedIds).containsExactlyElementsOf(startedIds);
    }

    private static <T> CompletableFuture<List<T>> collect(Flow.Publisher<T> publisher) {
        List<T> items = new ArrayList<>();
        CompletableFuture<List<T>> result = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T item) {
                items.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                result.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                result.complete(items);
            }
        });
        return result;
    }
}
