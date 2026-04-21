package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ImmediateIfLastReturnTest {

    interface Assistant {
        Result<String> chat(String message);
    }

    interface StreamingAssistant {
        TokenStream chat(String message);
    }

    static class SideEffectThenHaltTools {

        final AtomicInteger sideEffectCalls = new AtomicInteger();
        final AtomicInteger haltCalls = new AtomicInteger();

        @Tool
        public String doSideEffect() {
            sideEffectCalls.incrementAndGet();
            return "side-effect-done";
        }

        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE_IF_LAST)
        public String halt() {
            haltCalls.incrementAndGet();
            return "halted";
        }
    }

    static class HaltTool {

        final AtomicInteger calls = new AtomicInteger();

        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE_IF_LAST)
        public String halt() {
            return "halted-" + calls.incrementAndGet();
        }
    }

    static class ThrowingHaltTool {

        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE_IF_LAST)
        public String halt() {
            throw new RuntimeException("halt failed");
        }
    }

    @Test
    void should_return_immediately_when_immediate_if_last_tool_is_last_in_batch__sync() {
        SideEffectThenHaltTools tools = new SideEffectThenHaltTools();
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(batchCall("call-1", "doSideEffect", "call-2", "halt"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tools)
                .build();

        Result<String> result = assistant.chat("go");

        assertThat(tools.sideEffectCalls.get()).isEqualTo(1);
        assertThat(tools.haltCalls.get()).isEqualTo(1);
        assertThat(model.requests()).hasSize(1);
        assertThat(result.toolExecutions()).hasSize(2);
        assertThat(result.toolExecutions().get(1).result()).isEqualTo("halted");
    }

    @Test
    void should_not_return_immediately_when_immediate_if_last_tool_is_not_last_in_batch__sync() {
        SideEffectThenHaltTools tools = new SideEffectThenHaltTools();
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(
                batchCall("call-1", "halt", "call-2", "doSideEffect"), AiMessage.from("done"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tools)
                .build();

        Result<String> result = assistant.chat("go");

        assertThat(tools.sideEffectCalls.get()).isEqualTo(1);
        assertThat(tools.haltCalls.get()).isEqualTo(1);

        assertThat(model.requests()).hasSize(2);
        assertThat(result.content()).isEqualTo("done");
    }

    @Test
    void should_return_immediately_when_single_immediate_if_last_tool__sync() {
        HaltTool tool = new HaltTool();
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(singleCall("call-1", "halt"));

        Assistant assistant =
                AiServices.builder(Assistant.class).chatModel(model).tools(tool).build();

        Result<String> result = assistant.chat("go");

        assertThat(tool.calls.get()).isEqualTo(1);
        assertThat(model.requests()).hasSize(1);
        assertThat(result.toolExecutions()).hasSize(1);
        assertThat(result.toolExecutions().get(0).result()).isEqualTo("halted-1");
    }

    @Test
    void should_not_return_immediately_when_immediate_if_last_tool_throws__sync() {
        ThrowingHaltTool tool = new ThrowingHaltTool();
        ChatModelMock model =
                ChatModelMock.thatAlwaysResponds(singleCall("call-1", "halt"), AiMessage.from("recovered"));

        Assistant assistant =
                AiServices.builder(Assistant.class).chatModel(model).tools(tool).build();

        Result<String> result = assistant.chat("go");

        assertThat(model.requests()).hasSize(2);
        assertThat(result.content()).isEqualTo("recovered");
    }

    @Test
    void should_return_immediately_when_immediate_if_last_tool_is_last_in_batch__streaming() throws Exception {
        SideEffectThenHaltTools tools = new SideEffectThenHaltTools();
        StreamingChatModelMock model =
                StreamingChatModelMock.thatAlwaysStreams(batchCall("call-1", "doSideEffect", "call-2", "halt"));

        List<ToolExecution> observedExecutions = new ArrayList<>();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .tools(tools)
                .build();

        assistant
                .chat("go")
                .onPartialResponse(ignored -> {})
                .onToolExecuted(observedExecutions::add)
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        future.get(10, TimeUnit.SECONDS);

        assertThat(tools.sideEffectCalls.get()).isEqualTo(1);
        assertThat(tools.haltCalls.get()).isEqualTo(1);
        assertThat(model.requests()).hasSize(1);
        assertThat(observedExecutions).hasSize(2);
    }

    @Test
    void should_not_return_immediately_when_immediate_if_last_tool_is_not_last_in_batch__streaming() throws Exception {
        SideEffectThenHaltTools tools = new SideEffectThenHaltTools();
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                batchCall("call-1", "halt", "call-2", "doSideEffect"), AiMessage.from("done"));

        List<ToolExecution> observedExecutions = new ArrayList<>();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .tools(tools)
                .build();

        assistant
                .chat("go")
                .onPartialResponse(ignored -> {})
                .onToolExecuted(observedExecutions::add)
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        future.get(10, TimeUnit.SECONDS);

        assertThat(tools.sideEffectCalls.get()).isEqualTo(1);
        assertThat(tools.haltCalls.get()).isEqualTo(1);
        assertThat(model.requests()).hasSize(2);
        assertThat(observedExecutions).hasSize(2);
    }

    private static AiMessage singleCall(String id, String name) {
        return AiMessage.from(
                ToolExecutionRequest.builder().id(id).name(name).arguments("{}").build());
    }

    private static AiMessage batchCall(String id1, String name1, String id2, String name2) {
        return AiMessage.from(
                ToolExecutionRequest.builder()
                        .id(id1)
                        .name(name1)
                        .arguments("{}")
                        .build(),
                ToolExecutionRequest.builder()
                        .id(id2)
                        .name(name2)
                        .arguments("{}")
                        .build());
    }
}
