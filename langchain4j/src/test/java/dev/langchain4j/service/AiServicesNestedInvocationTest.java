package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.observability.api.event.AiServiceInvocationSummaryEvent;
import dev.langchain4j.observability.api.listener.AiServiceInvocationSummaryListener;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
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

    enum Execution {
        SYNC,
        ASYNC,
        PARALLEL
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
