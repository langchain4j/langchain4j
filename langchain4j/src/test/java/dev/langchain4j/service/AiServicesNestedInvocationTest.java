package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceInvocationSummaryEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.AiServiceInvocationSummaryListener;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
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

        TokenStream chatStream(String message);

        String chatWithProvidedUserMessage();
    }

    enum Execution {
        SYNC,
        ASYNC,
        PARALLEL,
        STREAMING
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
        AiMessage[] parentResponses = {AiMessage.from(requests), AiMessage.from("parent answer")};
        var builder = AiServices.builder(Assistant.class)
                .tools(new NestedTools(child))
                .registerListener((AiServiceInvocationSummaryListener) parents::add);
        if (execution == Execution.STREAMING) {
            builder.streamingChatModel(StreamingChatModelMock.thatAlwaysStreams(parentResponses));
        } else {
            builder.chatModel(ChatModelMock.thatAlwaysResponds(parentResponses));
        }
        if (execution == Execution.ASYNC || execution == Execution.PARALLEL) {
            builder.executeToolsConcurrently(executor);
        }
        Assistant parent = builder.build();

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
    void should_nest_a_child_that_has_no_summary_listener_of_its_own() {
        var parents = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        Assistant child = AiServices.builder(Assistant.class)
                .chatModel(new ChatModelMock("child answer"))
                .build();
        Assistant parent = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(
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

    enum PartRunBeforeTheStartedEvent {
        CHAT_MEMORY_PROVIDER,
        SYSTEM_MESSAGE_PROVIDER,
        USER_MESSAGE_PROVIDER,
        SYSTEM_MESSAGE_TRANSFORMER
    }

    @ParameterizedTest
    @EnumSource(PartRunBeforeTheStartedEvent.class)
    void should_link_but_not_nest_summaries_of_ai_services_called_before_the_started_event(
            PartRunBeforeTheStartedEvent part) {
        // These parts run before the AiServiceStartedEvent, so the child ends before the parent is tracked
        var children = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        var parents = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        Assistant child = childAssistant(children);
        var builder = AiServices.builder(Assistant.class)
                .chatModel(new ChatModelMock("parent answer"))
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
        Assistant parent = builder.build();

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
