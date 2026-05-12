package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StreamingToolExecutionSchedulingTest {

    interface StreamingAssistant {
        TokenStream chat(String message);
    }

    static class ThreadCapturingTool {

        final CountDownLatch started = new CountDownLatch(1);
        final AtomicReference<Thread> thread = new AtomicReference<>();

        @Tool
        public String doWork(String arg) {
            thread.set(Thread.currentThread());
            started.countDown();
            return "ok-" + arg;
        }
    }

    private ExecutorService toolExecutor;

    @BeforeEach
    void setUp() {
        ThreadFactory threadFactory = r -> {
            Thread thread = new Thread(r, "test-streaming-tool-executor");
            thread.setDaemon(true);
            return thread;
        };
        toolExecutor = Executors.newSingleThreadExecutor(threadFactory);
    }

    @AfterEach
    void tearDown() {
        if (toolExecutor != null) {
            toolExecutor.shutdownNow();
        }
    }

    @Test
    void default_zero_max_starts_tool_from_on_complete_tool_call_before_on_complete_response() throws Exception {
        ThreadCapturingTool tool = new ThreadCapturingTool();
        ToolExecutionRequest toolCall = toolCall("c1", "a");
        BlockingAfterFirstToolCallStreamingModel model =
                new BlockingAfterFirstToolCallStreamingModel(toolCall, tool.started);

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                .executeToolsConcurrently(toolExecutor)
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
        assertThat(model.toolStartedBeforeCompleteResponse.get())
                .as("the tool should be scheduled from onCompleteToolCall before onCompleteResponse is emitted")
                .isTrue();
    }

    @Test
    void single_streaming_tool_uses_tool_executor_when_concurrent_execution_is_configured() throws Exception {
        ThreadCapturingTool tool = new ThreadCapturingTool();
        ToolExecutionRequest toolCall = toolCall("c1", "a");
        BlockingAfterFirstToolCallStreamingModel model =
                new BlockingAfterFirstToolCallStreamingModel(toolCall, tool.started);

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                .executeToolsConcurrently(toolExecutor)
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
        assertThat(tool.thread.get()).isNotNull();
        assertThat(tool.thread.get().getName()).isEqualTo("test-streaming-tool-executor");
    }

    private static ToolExecutionRequest toolCall(String id, String arg) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name("doWork")
                .arguments("{\"arg0\": \"" + arg + "\"}")
                .build();
    }

    private static class BlockingAfterFirstToolCallStreamingModel implements StreamingChatModel {

        private final ToolExecutionRequest toolCall;
        private final CountDownLatch toolStarted;
        private final AtomicInteger invocations = new AtomicInteger();
        private final AtomicBoolean toolStartedBeforeCompleteResponse = new AtomicBoolean();

        private BlockingAfterFirstToolCallStreamingModel(ToolExecutionRequest toolCall, CountDownLatch toolStarted) {
            this.toolCall = toolCall;
            this.toolStarted = toolStarted;
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            int invocation = invocations.incrementAndGet();
            Thread thread = new Thread(() -> {
                if (invocation == 1) {
                    onCompleteToolCall(handler, new CompleteToolCall(0, toolCall));
                    try {
                        toolStartedBeforeCompleteResponse.set(toolStarted.await(500, TimeUnit.MILLISECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    onCompleteResponse(
                            handler,
                            ChatResponse.builder()
                                    .aiMessage(AiMessage.from(toolCall))
                                    .build());
                } else {
                    onCompleteResponse(
                            handler,
                            ChatResponse.builder().aiMessage(AiMessage.from("done")).build());
                }
            }, "test-streaming-model");
            thread.setDaemon(true);
            thread.start();
        }
    }
}
