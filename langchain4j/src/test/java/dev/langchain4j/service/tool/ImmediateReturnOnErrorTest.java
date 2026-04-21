package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
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

class ImmediateReturnOnErrorTest {

    interface Assistant {
        Result<String> chat(String message);
    }

    interface StreamingAssistant {
        TokenStream chat(String message);
    }

    static class ThrowingThenSucceedingTool {

        final AtomicInteger calls = new AtomicInteger();

        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
        public String doWork() {
            int call = calls.incrementAndGet();
            if (call == 1) {
                throw new IllegalArgumentException("bad input on call " + call);
            }
            return "ok-on-call-" + call;
        }
    }

    static class SucceedingTool {

        final AtomicInteger calls = new AtomicInteger();

        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
        public String doWork() {
            return "ok-on-call-" + calls.incrementAndGet();
        }
    }

    @Test
    void should_not_return_immediately_when_immediate_tool_throws__sync() {

        ThrowingThenSucceedingTool tool = new ThrowingThenSucceedingTool();
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(toolCall("call-1"), toolCall("call-2"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .build();

        Result<String> result = assistant.chat("go");

        assertThat(tool.calls.get()).isEqualTo(2);

        assertThat(model.requests()).hasSize(2);

        assertThat(model.requests().get(0).messages()).containsExactly(UserMessage.from("go"));

        List<ChatMessage> secondChatRequestMessages = model.requests().get(1).messages();
        assertThat(secondChatRequestMessages).hasSize(3);
        assertThat(secondChatRequestMessages.get(0)).isEqualTo(UserMessage.from("go"));
        assertThat(secondChatRequestMessages.get(1)).isInstanceOf(AiMessage.class);
        assertThat(secondChatRequestMessages.get(2))
                .isInstanceOfSatisfying(ToolExecutionResultMessage.class, toolResultMessage -> {
                    assertThat(toolResultMessage.id()).isEqualTo("call-1");
                    assertThat(toolResultMessage.toolName()).isEqualTo("doWork");
                    assertThat(toolResultMessage.isError()).isTrue();
                    assertThat(toolResultMessage.text()).isEqualTo("bad input on call 1");
                });

        assertThat(result.toolExecutions()).hasSize(2);
        assertThat(result.toolExecutions().get(0).request().id()).isEqualTo("call-1");
        assertThat(result.toolExecutions().get(0).result()).isEqualTo("bad input on call 1");
        assertThat(result.toolExecutions().get(1).request().id()).isEqualTo("call-2");
        assertThat(result.toolExecutions().get(1).result()).isEqualTo("ok-on-call-2");
    }

    @Test
    void should_return_immediately_when_immediate_tool_succeeds__sync() {

        SucceedingTool tool = new SucceedingTool();
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(toolCall("call-1"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .build();

        Result<String> result = assistant.chat("go");

        assertThat(tool.calls.get()).isEqualTo(1);

        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().get(0).messages()).containsExactly(UserMessage.from("go"));

        assertThat(result.toolExecutions()).hasSize(1);
        assertThat(result.toolExecutions().get(0).result()).isEqualTo("ok-on-call-1");
    }

    @Test
    void should_not_return_immediately_when_immediate_tool_throws__streaming() throws Exception {

        ThrowingThenSucceedingTool tool = new ThrowingThenSucceedingTool();
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(toolCall("call-1"), toolCall("call-2"));
        List<ToolExecution> observedExecutions = new ArrayList<>();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .tools(tool)
                .build();

        assistant
                .chat("go")
                .onPartialResponse(ignored -> {})
                .onToolExecuted(observedExecutions::add)
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        ChatResponse finalResponse = future.get(10, TimeUnit.SECONDS);

        assertThat(tool.calls.get()).isEqualTo(2);

        assertThat(model.requests()).hasSize(2);

        assertThat(model.requests().get(0).messages()).containsExactly(UserMessage.from("go"));

        List<ChatMessage> secondChatRequestMessages = model.requests().get(1).messages();
        assertThat(secondChatRequestMessages).hasSize(3);
        assertThat(secondChatRequestMessages.get(0)).isEqualTo(UserMessage.from("go"));
        assertThat(secondChatRequestMessages.get(1)).isInstanceOf(AiMessage.class);
        assertThat(secondChatRequestMessages.get(2))
                .isInstanceOfSatisfying(ToolExecutionResultMessage.class, errorMessage -> {
                    assertThat(errorMessage.toolName()).isEqualTo("doWork");
                    assertThat(errorMessage.isError()).isTrue();
                    assertThat(errorMessage.text()).isEqualTo("bad input on call 1");
                });

        assertThat(observedExecutions).hasSize(2);
        assertThat(observedExecutions.get(0).request().id()).isEqualTo("call-1");
        assertThat(observedExecutions.get(0).result()).isEqualTo("bad input on call 1");
        assertThat(observedExecutions.get(1).request().id()).isEqualTo("call-2");
        assertThat(observedExecutions.get(1).result()).isEqualTo("ok-on-call-2");

        assertThat(finalResponse.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(finalResponse.aiMessage().toolExecutionRequests().get(0).id()).isEqualTo("call-2");
    }

    @Test
    void should_return_immediately_when_immediate_tool_succeeds__streaming() throws Exception {

        SucceedingTool tool = new SucceedingTool();
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(toolCall("call-1"));
        List<ToolExecution> observedExecutions = new ArrayList<>();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .tools(tool)
                .build();

        assistant
                .chat("go")
                .onPartialResponse(ignored -> {})
                .onToolExecuted(observedExecutions::add)
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        future.get(10, TimeUnit.SECONDS);

        assertThat(tool.calls.get()).isEqualTo(1);

        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().get(0).messages()).containsExactly(UserMessage.from("go"));

        assertThat(observedExecutions).hasSize(1);
        assertThat(observedExecutions.get(0).request().id()).isEqualTo("call-1");
        assertThat(observedExecutions.get(0).result()).isEqualTo("ok-on-call-1");
    }

    private static AiMessage toolCall(String id) {
        return AiMessage.from(ToolExecutionRequest.builder()
                .id(id)
                .name("doWork")
                .arguments("{}")
                .build());
    }
}
