package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Validates {@link AiServices#maxToolCallsPerResponse(int)} cooperative truncation when an LLM
 * response contains more tool calls than the user wants to spend.
 */
class MaxToolCallsPerResponseTest {

    interface Assistant {
        String chat(String message);
    }

    interface StreamingAssistant {
        TokenStream chat(String message);
    }

    static class CountingTool {

        final AtomicInteger calls = new AtomicInteger();
        final List<String> args = new CopyOnWriteArrayList<>();

        @Tool
        public String doWork(String arg) {
            calls.incrementAndGet();
            args.add(arg);
            return "ok-" + arg;
        }
    }

    @Test
    void should_throw_when_response_has_more_tool_calls_than_limit__sync() {

        // given
        CountingTool tool = new CountingTool();
        // LLM returns a single response with 3 tool calls; limit is 2
        AiMessage threeToolCallResponse = AiMessage.from(toolCall("c1", "a"), toolCall("c2", "b"), toolCall("c3", "c"));
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(threeToolCallResponse);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                .maxToolCallsPerResponse(2)
                .build();

        // when / then
        assertThatExceptionOfType(ToolCallsLimitExceededException.class)
                .isThrownBy(() -> assistant.chat("go"))
                .matches(ex -> ex.getLimit() == 2 && ex.getAttempted() == 3);

        // No tools should have been executed since the cap is enforced before dispatch.
        assertThat(tool.calls.get()).isZero();
    }

    @Test
    void should_not_throw_when_response_has_exactly_limit_tool_calls__sync() {

        CountingTool tool = new CountingTool();
        AiMessage twoToolCalls = AiMessage.from(toolCall("c1", "a"), toolCall("c2", "b"));
        AiMessage finalAnswer = AiMessage.from("done");
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(twoToolCalls, finalAnswer);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                .maxToolCallsPerResponse(2)
                .build();

        assertThatNoException().isThrownBy(() -> assistant.chat("go"));
        assertThat(tool.calls.get()).isEqualTo(2);
    }

    @Test
    void should_be_unlimited_when_default_zero__sync() {

        CountingTool tool = new CountingTool();
        AiMessage manyToolCalls = AiMessage.from(
                toolCall("c1", "a"),
                toolCall("c2", "b"),
                toolCall("c3", "c"),
                toolCall("c4", "d"),
                toolCall("c5", "e"));
        AiMessage finalAnswer = AiMessage.from("done");
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(manyToolCalls, finalAnswer);

        // No maxToolCallsPerResponse set => unlimited (default 0).
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                .build();

        assertThatNoException().isThrownBy(() -> assistant.chat("go"));
        assertThat(tool.calls.get()).isEqualTo(5);
    }

    @Test
    void explicit_zero_means_unlimited__sync() {

        CountingTool tool = new CountingTool();
        AiMessage manyToolCalls = AiMessage.from(toolCall("c1", "a"), toolCall("c2", "b"), toolCall("c3", "c"));
        AiMessage finalAnswer = AiMessage.from("done");
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(manyToolCalls, finalAnswer);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                .maxToolCallsPerResponse(0)
                .build();

        assertThatNoException().isThrownBy(() -> assistant.chat("go"));
        assertThat(tool.calls.get()).isEqualTo(3);
    }

    @Test
    void should_throw_before_inline_dispatch_when_response_has_more_tool_calls_than_limit__streaming() throws Exception {

        CountingTool tool = new CountingTool();
        StreamingChatModel model = StreamingToolCallModel.withToolCalls(
                toolCall("c1", "a"),
                toolCall("c2", "b"),
                toolCall("c3", "c"),
                toolCall("c4", "d"));

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                .maxToolCallsPerResponse(2)
                .build();

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();
        assistant
                .chat("go")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(r -> futureError.completeExceptionally(
                        new IllegalStateException("onCompleteResponse should not be called")))
                .onError(futureError::complete)
                .start();

        Throwable error = futureError.get(10, TimeUnit.SECONDS);
        assertThat(error).isInstanceOf(ToolCallsLimitExceededException.class);
        ToolCallsLimitExceededException ex = (ToolCallsLimitExceededException) error;
        assertThat(ex.getLimit()).isEqualTo(2);
        assertThat(ex.getAttempted()).isEqualTo(4);
        // Without an executor, streaming tools still run inline during onCompleteResponse.
        // The limit is observed before that inline dispatch happens, so no tool runs.
        assertThat(tool.calls.get()).isZero();
        assertThat(tool.args).isEmpty();
    }

    @Test
    void should_throw_before_any_tool_runs_with_tool_executor__streaming() throws Exception {

        CountingTool tool = new CountingTool();
        StreamingChatModel model = StreamingToolCallModel.withToolCalls(
                toolCall("c1", "a"),
                toolCall("c2", "b"),
                toolCall("c3", "c"),
                toolCall("c4", "d"),
                toolCall("c5", "e"));

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                .maxToolCallsPerResponse(2)
                .executeToolsConcurrently(Runnable::run)
                .build();

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();
        assistant
                .chat("go")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(r -> futureError.completeExceptionally(
                        new IllegalStateException("onCompleteResponse should not be called")))
                .onError(futureError::complete)
                .start();

        Throwable error = futureError.get(10, TimeUnit.SECONDS);
        assertThat(error).isInstanceOf(ToolCallsLimitExceededException.class);
        ToolCallsLimitExceededException ex = (ToolCallsLimitExceededException) error;
        assertThat(ex.getLimit()).isEqualTo(2);
        assertThat(ex.getAttempted()).isEqualTo(5);
        assertThat(tool.calls.get()).isZero();
        assertThat(tool.args).isEmpty();
    }

    @Test
    void should_not_throw_when_response_under_limit__streaming() throws Exception {

        CountingTool tool = new CountingTool();
        AiMessage twoToolCalls = AiMessage.from(toolCall("c1", "a"), toolCall("c2", "b"));
        AiMessage finalAnswer = AiMessage.from("done");
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(twoToolCalls, finalAnswer);

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                .maxToolCallsPerResponse(2)
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
    }

    @Test
    void should_use_tool_executor_for_single_tool_when_streaming_cap_is_configured() throws Exception {

        CountingTool tool = new CountingTool();
        AtomicBoolean executorUsed = new AtomicBoolean();
        AiMessage oneToolCall = AiMessage.from(toolCall("c1", "a"));
        AiMessage finalAnswer = AiMessage.from("done");
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(oneToolCall, finalAnswer);

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                .maxToolCallsPerResponse(1)
                .executeToolsConcurrently(command -> {
                    executorUsed.set(true);
                    command.run();
                })
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
        assertThat(executorUsed.get()).isTrue();
    }

    private static ToolExecutionRequest toolCall(String id, String arg) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name("doWork")
                .arguments("{\"arg0\": \"" + arg + "\"}")
                .build();
    }

    private static class StreamingToolCallModel implements StreamingChatModel {

        private final List<ToolExecutionRequest> toolCalls;

        private StreamingToolCallModel(List<ToolExecutionRequest> toolCalls) {
            this.toolCalls = toolCalls;
        }

        static StreamingToolCallModel withToolCalls(ToolExecutionRequest... toolCalls) {
            return new StreamingToolCallModel(List.of(toolCalls));
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            Thread thread = new Thread(() -> {
                for (int i = 0; i < toolCalls.size(); i++) {
                    onCompleteToolCall(handler, new CompleteToolCall(i, toolCalls.get(i)));
                }
                onCompleteResponse(
                        handler,
                        ChatResponse.builder()
                                .aiMessage(AiMessage.from(toolCalls))
                                .build());
            }, "test-streaming-tool-call-model");
            thread.setDaemon(true);
            thread.start();
        }
    }
}
