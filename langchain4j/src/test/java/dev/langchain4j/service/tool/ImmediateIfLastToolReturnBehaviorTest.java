package dev.langchain4j.service.tool;

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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ImmediateIfLastToolReturnBehaviorTest {

    interface Assistant {
        Result<String> chat(String message);
    }

    interface StreamingAssistant {
        TokenStream chat(String message);
    }

    static class Tools {

        final AtomicInteger regularCalls = new AtomicInteger();
        final AtomicInteger finalizeCalls = new AtomicInteger();

        @Tool
        public String leftMouseClick() {
            regularCalls.incrementAndGet();
            return "clicked";
        }

        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE_IF_LAST)
        public String endExecutionAndGetFinalResult() {
            return "done-" + finalizeCalls.incrementAndGet();
        }
    }

    @Test
    void should_halt_when_immediate_if_last_tool_is_last_in_response__sync() {

        Tools tools = new Tools();
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(AiMessage.from(
                toolRequest("call-1", "leftMouseClick"),
                toolRequest("call-2", "endExecutionAndGetFinalResult")));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tools)
                .build();

        Result<String> result = assistant.chat("do it");

        assertThat(tools.regularCalls.get()).isEqualTo(1);
        assertThat(tools.finalizeCalls.get()).isEqualTo(1);

        // Should halt after the first LLM call — no extra round trip
        assertThat(model.requests()).hasSize(1);

        assertThat(result.toolExecutions()).hasSize(2);
        assertThat(result.toolExecutions().get(0).request().name()).isEqualTo("leftMouseClick");
        assertThat(result.toolExecutions().get(1).request().name()).isEqualTo("endExecutionAndGetFinalResult");
        assertThat(result.content()).isNull();
    }

    @Test
    void should_halt_when_only_tool_call_is_immediate_if_last__sync() {

        Tools tools = new Tools();
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(toolRequest("call-1", "endExecutionAndGetFinalResult")));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tools)
                .build();

        Result<String> result = assistant.chat("finish");

        assertThat(tools.regularCalls.get()).isZero();
        assertThat(tools.finalizeCalls.get()).isEqualTo(1);

        assertThat(model.requests()).hasSize(1);
        assertThat(result.toolExecutions()).hasSize(1);
        assertThat(result.toolExecutions().get(0).request().name()).isEqualTo("endExecutionAndGetFinalResult");
        assertThat(result.content()).isNull();
    }

    @Test
    void should_not_halt_when_immediate_if_last_tool_is_not_last_in_response__sync() {

        Tools tools = new Tools();
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(
                        toolRequest("call-1", "endExecutionAndGetFinalResult"),
                        toolRequest("call-2", "leftMouseClick")),
                AiMessage.from("final answer"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tools)
                .build();

        Result<String> result = assistant.chat("do it wrong");

        assertThat(tools.finalizeCalls.get()).isEqualTo(1);
        assertThat(tools.regularCalls.get()).isEqualTo(1);

        // Should continue — two LLM requests made
        assertThat(model.requests()).hasSize(2);
        assertThat(result.content()).isEqualTo("final answer");
    }

    @Test
    void should_not_halt_when_immediate_if_last_tool_errors__sync() {

        AtomicInteger calls = new AtomicInteger();
        class ThrowingTools {
            @Tool
            public String leftMouseClick() {
                return "clicked";
            }

            @Tool(returnBehavior = ReturnBehavior.IMMEDIATE_IF_LAST)
            public String endExecutionAndGetFinalResult() {
                int call = calls.incrementAndGet();
                if (call == 1) {
                    throw new IllegalStateException("bad finalize");
                }
                return "done";
            }
        }

        ThrowingTools tools = new ThrowingTools();
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(
                        toolRequest("call-1", "leftMouseClick"),
                        toolRequest("call-2", "endExecutionAndGetFinalResult")),
                AiMessage.from("final answer"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tools)
                .build();

        Result<String> result = assistant.chat("go");

        // Errored IMMEDIATE_IF_LAST should NOT halt — loop continues and LLM gets the error
        assertThat(model.requests()).hasSize(2);
        assertThat(result.content()).isEqualTo("final answer");
    }

    @Test
    void should_halt_when_immediate_if_last_tool_is_last_in_response__streaming() throws Exception {

        Tools tools = new Tools();
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(AiMessage.from(
                toolRequest("call-1", "leftMouseClick"),
                toolRequest("call-2", "endExecutionAndGetFinalResult")));
        List<ToolExecution> observedExecutions = new ArrayList<>();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .tools(tools)
                .build();

        assistant
                .chat("do it")
                .onPartialResponse(ignored -> {
                })
                .onToolExecuted(observedExecutions::add)
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        ChatResponse finalResponse = future.get(10, TimeUnit.SECONDS);

        assertThat(tools.regularCalls.get()).isEqualTo(1);
        assertThat(tools.finalizeCalls.get()).isEqualTo(1);

        assertThat(model.requests()).hasSize(1);

        assertThat(observedExecutions).hasSize(2);
        assertThat(observedExecutions.get(0).request().name()).isEqualTo("leftMouseClick");
        assertThat(observedExecutions.get(1).request().name()).isEqualTo("endExecutionAndGetFinalResult");

        assertThat(finalResponse.aiMessage().hasToolExecutionRequests()).isTrue();
    }

    @Test
    void should_not_halt_when_immediate_if_last_tool_is_not_last_in_response__streaming() throws Exception {

        Tools tools = new Tools();
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(
                        toolRequest("call-1", "endExecutionAndGetFinalResult"),
                        toolRequest("call-2", "leftMouseClick")),
                AiMessage.from("final answer"));
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .tools(tools)
                .build();

        assistant
                .chat("do it wrong")
                .onPartialResponse(ignored -> {
                })
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        ChatResponse finalResponse = future.get(10, TimeUnit.SECONDS);

        assertThat(tools.finalizeCalls.get()).isEqualTo(1);
        assertThat(tools.regularCalls.get()).isEqualTo(1);
        assertThat(model.requests()).hasSize(2);
        assertThat(finalResponse.aiMessage().text()).isEqualTo("final answer");
    }

    private static ToolExecutionRequest toolRequest(String id, String name) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(name)
                .arguments("{}")
                .build();
    }
}
