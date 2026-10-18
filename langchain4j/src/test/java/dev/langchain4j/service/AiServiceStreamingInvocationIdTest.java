package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.listener.AiServiceCompletedListener;
import dev.langchain4j.observability.api.listener.AiServiceStartedListener;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiServiceStreamingInvocationIdTest {

    interface EventStreamer {
        Flow.Publisher<AiServiceStreamingEvent> chat(String message);
    }

    interface BoxStringStreamer {
        ReactiveBox<String> chat(String message);
    }

    @Test
    void resubscriptions_use_distinct_ids_throughout_the_interaction() throws Exception {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("tool-1")
                .name("temperature")
                .arguments("{}")
                .build();
        StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                AiMessage.from(toolRequest), AiMessage.from("42"),
                AiMessage.from(toolRequest), AiMessage.from("42"));
        List<InvocationContext> started = new CopyOnWriteArrayList<>();
        List<InvocationContext> completed = new CopyOnWriteArrayList<>();
        List<InvocationContext> augmented = new CopyOnWriteArrayList<>();
        List<InvocationContext> guarded = new CopyOnWriteArrayList<>();
        List<InvocationContext> executed = new CopyOnWriteArrayList<>();
        RetrievalAugmentor augmentor = new RetrievalAugmentor() {
            @Override
            public AugmentationResult augment(AugmentationRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<AugmentationResult> augmentAsync(AugmentationRequest request) {
                augmented.add(request.metadata().invocationContext());
                return CompletableFuture.completedFuture(new AugmentationResult(request.chatMessage(), List.of()));
            }
        };
        InputGuardrail guardrail = new InputGuardrail() {
            @Override
            public CompletableFuture<InputGuardrailResult> validateAsync(InputGuardrailRequest request) {
                guarded.add(request.requestParams().invocationContext());
                return CompletableFuture.completedFuture(success());
            }
        };
        ToolExecutor tool = new ToolExecutor() {
            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<ToolExecutionResult> executeAsync(
                    ToolExecutionRequest request, InvocationContext context) {
                executed.add(context);
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.builder().resultText("42").build());
            }
        };
        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model)
                .retrievalAugmentor(augmentor)
                .inputGuardrails(guardrail)
                .toolProvider(request -> ToolProviderResult.builder()
                        .add(ToolSpecification.builder().name("temperature").build(), tool)
                        .build())
                .registerListener((AiServiceStartedListener) event -> started.add(event.invocationContext()))
                .registerListener((AiServiceCompletedListener) event -> completed.add(event.invocationContext()))
                .build();

        Flow.Publisher<AiServiceStreamingEvent> publisher = assistant.chat("temperature?");
        assertThat(started).isEmpty();
        List<AiServiceStreamingEvent> first = collect(publisher).get(10, TimeUnit.SECONDS);
        List<AiServiceStreamingEvent> second = collect(publisher).get(10, TimeUnit.SECONDS);

        assertThat(started).hasSize(2);
        assertThat(started).extracting(InvocationContext::invocationId).doesNotHaveDuplicates();
        for (List<InvocationContext> contexts : List.of(augmented, guarded, executed, completed)) {
            assertThat(contexts)
                    .extracting(InvocationContext::invocationId)
                    .containsExactlyElementsOf(started.stream()
                            .map(InvocationContext::invocationId)
                            .toList());
        }
        assertThat(first)
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.invocationContext().invocationId())
                        .isEqualTo(started.get(0).invocationId()));
        assertThat(second)
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.invocationContext().invocationId())
                        .isEqualTo(started.get(1).invocationId()));
        assertThat(started.get(1))
                .usingRecursiveComparison()
                .ignoringFields("invocationId")
                .isEqualTo(started.get(0));
        assertThat(model.requests()).hasSize(4);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrent_subscriptions_have_independent_ids(boolean adaptedTextStream) throws Exception {
        int subscribers = 8;
        StreamingEventChatModelMock model =
                new StreamingEventChatModelMock(java.util.Collections.nCopies(subscribers, AiMessage.from("Hello")));
        List<InvocationContext> started = new CopyOnWriteArrayList<>();
        List<InvocationContext> completed = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> release = new CompletableFuture<>();
        RetrievalAugmentor augmentor = new RetrievalAugmentor() {
            @Override
            public AugmentationResult augment(AugmentationRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<AugmentationResult> augmentAsync(AugmentationRequest request) {
                return release.thenApply(ignored -> new AugmentationResult(request.chatMessage(), List.of()));
            }
        };
        AiServices<?> builder = adaptedTextStream
                ? AiServices.builder(BoxStringStreamer.class)
                : AiServices.builder(EventStreamer.class);
        builder.streamingChatModel(model)
                .retrievalAugmentor(augmentor)
                .registerListener((AiServiceStartedListener) event -> started.add(event.invocationContext()))
                .registerListener((AiServiceCompletedListener) event -> completed.add(event.invocationContext()));
        Object assistant = builder.build();
        Flow.Publisher<?> publisher = adaptedTextStream
                ? ((BoxStringStreamer) assistant).chat("Hi").publisher()
                : ((EventStreamer) assistant).chat("Hi");
        var executor = Executors.newFixedThreadPool(subscribers);
        CyclicBarrier barrier = new CyclicBarrier(subscribers);
        try {
            List<java.util.concurrent.Future<CompletableFuture<? extends List<?>>>> subscriptions = new ArrayList<>();
            for (int i = 0; i < subscribers; i++) {
                subscriptions.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return collect(publisher);
                }));
            }
            List<CompletableFuture<? extends List<?>>> results = new ArrayList<>();
            for (var subscription : subscriptions) {
                results.add(subscription.get(10, TimeUnit.SECONDS));
            }
            // Every subscription is in flight before any model response is released.
            assertThat(started).hasSize(subscribers);
            assertThat(completed).isEmpty();
            release.complete(null);
            for (var result : results) {
                List<?> items = result.get(10, TimeUnit.SECONDS);
                assertThat(items).isNotEmpty();
                if (adaptedTextStream) {
                    assertThat(items.stream().map(Object::toString).reduce("", String::concat))
                            .isEqualTo("Hello");
                } else {
                    assertThat(items.stream()
                                    .map(AiServiceStreamingEvent.class::cast)
                                    .map(event -> event.invocationContext().invocationId())
                                    .distinct()
                                    .toList())
                            .hasSize(1);
                }
            }
            assertThat(started).extracting(InvocationContext::invocationId).doesNotHaveDuplicates();
            assertThat(completed)
                    .extracting(InvocationContext::invocationId)
                    .containsExactlyInAnyOrderElementsOf(started.stream()
                            .map(InvocationContext::invocationId)
                            .toList());
            assertThat(model.requests()).hasSize(subscribers);
        } finally {
            release.complete(null);
            executor.shutdownNow();
        }
    }

    private static <T> CompletableFuture<List<T>> collect(Flow.Publisher<T> publisher) {
        CompletableFuture<List<T>> result = new CompletableFuture<>();
        List<T> items = new ArrayList<>();
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
            public void onError(Throwable error) {
                result.completeExceptionally(error);
            }

            @Override
            public void onComplete() {
                result.complete(items);
            }
        });
        return result;
    }
}
