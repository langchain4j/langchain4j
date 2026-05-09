package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Validates {@link AiServices#streamingToolDispatchHook(StreamingToolDispatchHook)} — the
 * integration SPI used by downstream framework integrators (e.g. {@code quarkus-langchain4j})
 * to control threading and context propagation around the streaming tool batch dispatch.
 */
class StreamingToolDispatchHookTest {

    interface StreamingAssistant {
        TokenStream chat(String message);
    }

    static class CountingTool {
        final AtomicInteger calls = new AtomicInteger();

        @Tool
        public String doWork(String arg) {
            calls.incrementAndGet();
            return "ok-" + arg;
        }
    }

    @Test
    void framework_dispatch_hook_receives_the_tool_batch_dispatch() throws Exception {
        CountingTool tool = new CountingTool();

        AiMessage twoToolCalls =
                AiMessage.from(toolCall("c1", "a"), toolCall("c2", "b"));
        AiMessage finalAnswer = AiMessage.from("done");
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(twoToolCalls, finalAnswer);

        AtomicInteger hookInvocations = new AtomicInteger();
        AtomicReference<Thread> hookThread = new AtomicReference<>();
        StreamingToolDispatchHook hook = new StreamingToolDispatchHook() {
            @Override
            public <T> CompletionStage<T> dispatch(Supplier<T> work) {
                hookInvocations.incrementAndGet();
                hookThread.set(Thread.currentThread());
                // Run inline (just like INLINE) so the test stays deterministic, but record
                // that the hook was invoked.
                try {
                    return CompletableFuture.completedFuture(work.get());
                } catch (Throwable t) {
                    CompletableFuture<T> failed = new CompletableFuture<>();
                    failed.completeExceptionally(t);
                    return failed;
                }
            }
        };

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                .streamingToolDispatchHook(hook)
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant
                .chat("go")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        ChatResponse response = future.get(10, TimeUnit.SECONDS);
        assertThat(response.aiMessage().text()).isEqualTo("done");
        assertThat(tool.calls.get()).isEqualTo(2);
        // The hook is invoked once per LLM response that contains tool calls.
        assertThat(hookInvocations.get())
                .as("hook should be invoked once for the response that contains tool calls")
                .isEqualTo(1);
        assertThat(hookThread.get()).isNotNull();
    }

    @Test
    void without_hook_default_inline_dispatch_runs_tools() throws Exception {
        CountingTool tool = new CountingTool();
        AiMessage oneToolCall = AiMessage.from(toolCall("c1", "a"));
        AiMessage finalAnswer = AiMessage.from("done");
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(oneToolCall, finalAnswer);

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                // No hook configured — the INLINE default runs inline.
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant
                .chat("go")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        ChatResponse response = future.get(10, TimeUnit.SECONDS);
        assertThat(response.aiMessage().text()).isEqualTo("done");
        assertThat(tool.calls.get()).isEqualTo(1);
    }

    private static ToolExecutionRequest toolCall(String id, String arg) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name("doWork")
                .arguments("{\"arg0\": \"" + arg + "\"}")
                .build();
    }
}
