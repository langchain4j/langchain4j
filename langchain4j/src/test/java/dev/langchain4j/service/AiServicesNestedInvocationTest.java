package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceInvocationSummaryEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.listener.AiServiceInvocationSummaryListener;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.observability.api.listener.AiServiceStartedListener;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AiServicesNestedInvocationTest {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void cleanUp() {
        executor.shutdownNow();
    }

    interface Assistant {
        String chat(String message);

        CompletableFuture<String> chatAsync(String message);
    }

    interface StreamingAssistant {
        TokenStream chat(String message);

        Flow.Publisher<String> chatReactive(String message);
    }

    enum Execution {
        SYNC,
        ASYNC,
        PARALLEL
    }

    enum Mode {
        SYNC,
        ASYNC,
        TOKEN_STREAM,
        REACTIVE
    }

    @ParameterizedTest
    @EnumSource(Execution.class)
    void should_nest_child_summaries_and_still_fire_them_standalone(Execution execution) throws Exception {
        var children = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        var parents = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        var bothChildrenStarted = new CountDownLatch(execution == Execution.PARALLEL ? 2 : 1);
        Assistant child = AiServices.builder(Assistant.class)
                .chatModel(new ChatModelMock(request -> {
                    bothChildrenStarted.countDown();
                    try {
                        assertThat(bothChildrenStarted.await(10, SECONDS)).isTrue();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    return AiMessage.from("child answer");
                }))
                .registerListener((AiServiceInvocationSummaryListener) children::add)
                .build();
        String toolName = execution == Execution.ASYNC ? "nestedAsync" : "nested";
        List<ToolExecutionRequest> requests = execution == Execution.PARALLEL
                ? List.of(toolCall("1", toolName), toolCall("2", toolName))
                : List.of(toolCall("1", toolName));
        var builder = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(AiMessage.from(requests), AiMessage.from("parent answer")))
                .tools(new NestedTools(child))
                .registerListener((AiServiceInvocationSummaryListener) parents::add);
        if (execution != Execution.SYNC) {
            builder.executeToolsConcurrently(executor);
        }
        Assistant parent = builder.build();

        String answer =
                execution == Execution.ASYNC ? parent.chatAsync("question").get(10, SECONDS) : parent.chat("question");

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
    void should_nest_summaries_of_ai_services_called_by_the_chat_memory_store() {
        var children = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        var parents = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        Assistant child = childAssistant(children);
        AtomicInteger childCalls = new AtomicInteger();
        Assistant parent = AiServices.builder(Assistant.class)
                .chatModel(new ChatModelMock("parent answer"))
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

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_nest_summaries_of_ai_services_called_by_the_chat_memory_provider(Mode mode) throws Exception {
        var children = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        var parents = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        Assistant child = childAssistant(children);

        String answer = invokeParent(
                mode,
                memoryId -> {
                    child.chat("create a chat memory");
                    return MessageWindowChatMemory.withMaxMessages(10);
                },
                (AiServiceInvocationSummaryListener) parents::add);

        assertThat(answer).isEqualTo("parent answer");

        assertChildrenNestedInParent(children, parents, 1);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_call_the_chat_memory_provider_after_the_invocation_has_started(Mode mode) throws Exception {
        // An AI Service called by a provider that runs before the AiServiceStartedEvent cannot be nested
        var calls = new CopyOnWriteArrayList<String>();

        invokeParent(
                mode,
                memoryId -> {
                    calls.add("chat memory provider");
                    return MessageWindowChatMemory.withMaxMessages(10);
                },
                (AiServiceStartedListener) event -> calls.add("started"));

        assertThat(calls).containsExactly("started", "chat memory provider");
    }

    private static String invokeParent(Mode mode, ChatMemoryProvider chatMemoryProvider, AiServiceListener<?> listener)
            throws Exception {
        if (mode == Mode.SYNC || mode == Mode.ASYNC) {
            Assistant parent = AiServices.builder(Assistant.class)
                    .chatModel(new ChatModelMock("parent answer"))
                    .chatMemoryProvider(chatMemoryProvider)
                    .registerListeners(listener)
                    .build();
            return mode == Mode.ASYNC ? parent.chatAsync("question").get(10, SECONDS) : parent.chat("question");
        }

        StreamingAssistant parent = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("parent ", "answer"))
                .chatMemoryProvider(chatMemoryProvider)
                .registerListeners(listener)
                .build();
        CompletableFuture<String> answer = new CompletableFuture<>();
        if (mode == Mode.TOKEN_STREAM) {
            parent.chat("question")
                    .onPartialResponse(partialResponse -> {})
                    .onCompleteResponse(
                            response -> answer.complete(response.aiMessage().text()))
                    .onError(answer::completeExceptionally)
                    .start();
        } else {
            StringBuilder text = new StringBuilder();
            parent.chatReactive("question").subscribe(new Flow.Subscriber<>() {

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(String token) {
                    text.append(token);
                }

                @Override
                public void onError(Throwable error) {
                    answer.completeExceptionally(error);
                }

                @Override
                public void onComplete() {
                    answer.complete(text.toString());
                }
            });
        }
        return answer.get(10, SECONDS);
    }

    private static Assistant childAssistant(List<AiServiceInvocationSummaryEvent> children) {
        return AiServices.builder(Assistant.class)
                .chatModel(new ChatModelMock("child answer"))
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

    static class NestedTools {
        private final Assistant child;

        NestedTools(Assistant child) {
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
