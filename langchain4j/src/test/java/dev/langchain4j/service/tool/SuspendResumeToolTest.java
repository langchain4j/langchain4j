package dev.langchain4j.service.tool;

import static dev.langchain4j.agent.tool.ReturnBehavior.SUSPEND;
import static dev.langchain4j.agent.tool.ReturnBehavior.TO_LLM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SuspendResumeToolTest {

    interface Assistant extends ToolExecutionResumer, ChatMemoryAccess {
        Result<String> chat(@MemoryId String conversationId, @UserMessage String message);
    }

    interface AssistantWithoutResult extends ToolExecutionResumer {
        String chat(@MemoryId String conversationId, @UserMessage String message);
    }

    interface StreamingAssistant extends StreamingToolExecutionResumer, ChatMemoryAccess {
        TokenStream chat(@MemoryId String conversationId, @UserMessage String message);
    }

    static class Tools {

        boolean askHumanExecuted;
        boolean lookupExecuted;

        @Tool(returnBehavior = SUSPEND)
        public String askHuman(String question) {
            askHumanExecuted = true;
            return "awaiting human input";
        }

        @Tool(returnBehavior = TO_LLM)
        public String lookup(String key) {
            lookupExecuted = true;
            return "looked-up:" + key;
        }
    }

    @Test
    void should_suspend_loop_and_resume_with_real_tool_result() {

        ToolExecutionRequest suspendCall = ToolExecutionRequest.builder()
                .id("call-1")
                .name("askHuman")
                .arguments("{\"question\": \"pick a seat\"}")
                .build();

        ChatModelMock model =
                ChatModelMock.thatAlwaysResponds(AiMessage.from(suspendCall), AiMessage.from("seat booked"));

        Tools tools = new Tools();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(20))
                .tools(tools)
                .build();

        Result<String> suspended = assistant.chat("conv-1", "book me a seat");

        assertThat(model.requests()).hasSize(1);
        assertThat(tools.askHumanExecuted).isTrue();
        assertThat(suspended.content()).isNull();
        assertThat(suspended.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
        assertThat(suspended.finalResponse().aiMessage().hasToolExecutionRequests())
                .isTrue();

        List<ToolExecutionRequest> pending =
                suspended.finalResponse().aiMessage().toolExecutionRequests();
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).name()).isEqualTo("askHuman");

        ToolExecutionResultMessage toolResult = ToolExecutionResultMessage.from(pending.get(0), "seat 12A");
        Result<String> resumed = assistant.resume("conv-1", toolResult);

        assertThat(model.requests()).hasSize(2);
        assertThat(resumed.content()).isEqualTo("seat booked");

        assertThat(assistant.getChatMemory("conv-1").messages())
                .anySatisfy(message -> assertThat(message).isInstanceOf(ToolExecutionResultMessage.class));
    }

    @Test
    void should_record_non_suspended_tool_results_when_suspending() {

        ToolExecutionRequest lookupCall = ToolExecutionRequest.builder()
                .id("call-1")
                .name("lookup")
                .arguments("{\"key\": \"price\"}")
                .build();
        ToolExecutionRequest suspendCall = ToolExecutionRequest.builder()
                .id("call-2")
                .name("askHuman")
                .arguments("{\"question\": \"confirm?\"}")
                .build();

        ChatModelMock model =
                ChatModelMock.thatAlwaysResponds(AiMessage.from(lookupCall, suspendCall), AiMessage.from("confirmed"));

        Tools tools = new Tools();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(20))
                .tools(tools)
                .build();

        Result<String> suspended = assistant.chat("conv-2", "buy if cheap");

        assertThat(tools.lookupExecuted).isTrue();
        assertThat(tools.askHumanExecuted).isTrue();
        assertThat(suspended.content()).isNull();
        assertThat(suspended.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);

        List<ToolExecutionResultMessage> recordedResults = assistant.getChatMemory("conv-2").messages().stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .toList();
        assertThat(recordedResults)
                .as("only the non-suspended tool result is recorded; the SUSPEND tool call stays pending")
                .hasSize(1);
        assertThat(recordedResults.get(0).toolName()).isEqualTo("lookup");

        ToolExecutionResultMessage toolResult = ToolExecutionResultMessage.from(suspendCall, "yes");
        Result<String> resumed = assistant.resume("conv-2", toolResult);

        assertThat(resumed.content()).isEqualTo("confirmed");
    }

    @Test
    void should_throw_when_suspend_tool_used_without_Result_return_type() {

        ToolExecutionRequest suspendCall = ToolExecutionRequest.builder()
                .id("call-1")
                .name("askHuman")
                .arguments("{\"question\": \"pick a seat\"}")
                .build();

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(AiMessage.from(suspendCall));

        AssistantWithoutResult assistant = AiServices.builder(AssistantWithoutResult.class)
                .chatModel(model)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(20))
                .tools(new Tools())
                .build();

        assertThatThrownBy(() -> assistant.chat("conv-3", "book me a seat"))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("SUSPEND")
                .hasMessageContaining("Result");
    }

    @Test
    void should_suspend_streaming_loop() throws Exception {

        ToolExecutionRequest suspendCall = ToolExecutionRequest.builder()
                .id("call-1")
                .name("askHuman")
                .arguments("{\"question\": \"pick a seat\"}")
                .build();

        StreamingChatModelMock model =
                StreamingChatModelMock.thatAlwaysStreams(AiMessage.from(suspendCall), AiMessage.from("seat booked"));

        Tools tools = new Tools();
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(20))
                .tools(tools)
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant
                .chat("conv-stream", "book me a seat")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        ChatResponse finalResponse = future.get(10, TimeUnit.SECONDS);

        assertThat(model.requests())
                .as("suspend stops the loop after a single LLM call, with no reprocessing round trip")
                .hasSize(1);
        assertThat(tools.askHumanExecuted).isTrue();
        assertThat(finalResponse.aiMessage().hasToolExecutionRequests())
                .as("the suspended tool call is carried in the final response, left pending in memory")
                .isTrue();
        assertThat(finalResponse.aiMessage().toolExecutionRequests().get(0).name())
                .isEqualTo("askHuman");
    }

    @Test
    void should_resume_streaming_loop_with_real_tool_result() throws Exception {

        ToolExecutionRequest suspendCall = ToolExecutionRequest.builder()
                .id("call-1")
                .name("askHuman")
                .arguments("{\"question\": \"pick a seat\"}")
                .build();

        StreamingChatModelMock model =
                StreamingChatModelMock.thatAlwaysStreams(AiMessage.from(suspendCall), AiMessage.from("seat booked"));

        Tools tools = new Tools();
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(20))
                .tools(tools)
                .build();

        CompletableFuture<ChatResponse> suspendedFuture = new CompletableFuture<>();
        assistant
                .chat("conv-sr", "book me a seat")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(suspendedFuture::complete)
                .onError(suspendedFuture::completeExceptionally)
                .start();

        ChatResponse suspended = suspendedFuture.get(10, TimeUnit.SECONDS);
        ToolExecutionRequest pending =
                suspended.aiMessage().toolExecutionRequests().get(0);

        ToolExecutionResultMessage toolResult = ToolExecutionResultMessage.from(pending, "seat 12A");

        CompletableFuture<ChatResponse> resumedFuture = new CompletableFuture<>();
        assistant
                .resume("conv-sr", toolResult)
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(resumedFuture::complete)
                .onError(resumedFuture::completeExceptionally)
                .start();

        ChatResponse resumed = resumedFuture.get(10, TimeUnit.SECONDS);

        assertThat(model.requests()).hasSize(2);
        assertThat(resumed.aiMessage().text()).isEqualTo("seat booked");

        assertThat(assistant.getChatMemory("conv-sr").messages())
                .anySatisfy(message -> assertThat(message).isInstanceOf(ToolExecutionResultMessage.class));
    }
}
