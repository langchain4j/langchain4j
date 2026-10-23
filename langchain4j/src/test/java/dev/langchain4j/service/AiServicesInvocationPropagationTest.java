package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.invocation.CurrentInvocation;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.observability.api.event.AiServiceInvocationSummaryEvent;
import dev.langchain4j.observability.api.listener.AiServiceInvocationSummaryListener;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AiServicesInvocationPropagationTest {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void cleanUp() {
        executor.shutdownNow();
    }

    interface Assistant {
        String chat(String message);

        CompletableFuture<String> chatAsync(String message);
    }

    @Test
    void should_clear_the_scope_after_a_tool_fails_on_a_reused_worker() throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        try {
            Assistant failing = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds(AiMessage.from(toolCall("1", "fail"))))
                    .tools(new FailingTool())
                    .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                    .executeToolsConcurrently(worker)
                    .build();
            assertThatThrownBy(() -> failing.chatAsync("question").get(10, SECONDS))
                    .hasRootCauseMessage("tool failure");
            var summaries = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
            Assistant independent = AiServices.builder(Assistant.class)
                    .chatModel(new ChatModelMock("independent answer"))
                    .registerListener((AiServiceInvocationSummaryListener) summaries::add)
                    .build();

            assertThat(worker.submit(() -> {
                                assertThat(CurrentInvocation.invocationId()).isNull();
                                return independent.chat("question");
                            })
                            .get(10, SECONDS))
                    .isEqualTo("independent answer");
            assertThat(summaries).hasSize(1);
            assertThat(summaries.get(0).invocationContext().parentInvocationId())
                    .isNull();
            assertThat(summaries.get(0).events())
                    .hasSize(4)
                    .noneMatch(AiServiceInvocationSummaryEvent.class::isInstance);
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void should_not_nest_a_child_invoked_from_a_thread_started_by_the_tool() {
        var children = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        var parents = new CopyOnWriteArrayList<AiServiceInvocationSummaryEvent>();
        Assistant child = AiServices.builder(Assistant.class)
                .chatModel(new ChatModelMock("child answer"))
                .registerListener((AiServiceInvocationSummaryListener) children::add)
                .build();
        Assistant parent = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(
                        AiMessage.from(toolCall("1", "nestedOnOwnThread")), AiMessage.from("parent answer")))
                .tools(new OwnThreadTool(child, executor))
                .registerListener((AiServiceInvocationSummaryListener) parents::add)
                .build();

        assertThat(parent.chat("question")).isEqualTo("parent answer");

        assertThat(children).hasSize(1);
        assertThat(children.get(0).invocationContext().parentInvocationId()).isNull();
        assertThat(parents).hasSize(1);
        assertThat(parents.get(0).events()).noneMatch(AiServiceInvocationSummaryEvent.class::isInstance);
    }

    private static ToolExecutionRequest toolCall(String id, String name) {
        return ToolExecutionRequest.builder().id(id).name(name).arguments("{}").build();
    }

    static class OwnThreadTool {
        private final Assistant child;
        private final ExecutorService executor;

        OwnThreadTool(Assistant child, ExecutorService executor) {
            this.child = child;
            this.executor = executor;
        }

        @Tool
        String nestedOnOwnThread() throws Exception {
            return CompletableFuture.supplyAsync(() -> child.chat("child question"), executor)
                    .get(10, SECONDS);
        }
    }

    static class FailingTool {
        @Tool
        String fail() {
            throw new IllegalStateException("tool failure");
        }
    }
}
