package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

/**
 * A response cut off by the output token limit (finish reason {@code LENGTH}) can hold tool calls with missing or
 * incomplete arguments. AI Services must not run them, in any mode, and must not leave the chat memory with a tool call
 * that has no result, because providers reject such a history.
 */
class AiServiceTruncatedToolCallTest {

    private static final String REFUSAL = "cut off by the output token limit";

    private static final ToolExecutionRequest PURGE_TMP = ToolExecutionRequest.builder()
            .id("call-1")
            .name("purge")
            .arguments("{\"arg0\": \"/tmp\"}")
            .build();

    // the output was cut right after the tool name, before any argument
    private static final ToolExecutionRequest PURGE_CUT_OFF = ToolExecutionRequest.builder()
            .id("call-2")
            .name("purge")
            .arguments("")
            .build();

    static class FileTools {

        final List<String> invocations = new CopyOnWriteArrayList<>();

        @Tool("Delete all files under a directory")
        String purge(String path) {
            invocations.add("purge(" + path + ")");
            return "purged " + path;
        }
    }

    interface Assistant {

        String chat(String message);
    }

    interface ResultAssistant {

        Result<String> chat(String message);
    }

    interface AsyncAssistant {

        CompletableFuture<String> chat(String message);
    }

    interface TokenStreamAssistant {

        TokenStream chat(String message);
    }

    interface ReactiveAssistant {

        Flow.Publisher<AiServiceStreamingEvent> chat(String message);
    }

    static class EventCollector implements Flow.Subscriber<AiServiceStreamingEvent> {

        final CompletableFuture<Void> terminated = new CompletableFuture<>();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(AiServiceStreamingEvent event) {}

        @Override
        public void onError(Throwable throwable) {
            terminated.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            terminated.complete(null);
        }
    }

    private static ChatResponse cutOff(AiMessage aiMessage) {
        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .finishReason(FinishReason.LENGTH)
                .build();
    }

    private static ChatResponse cutOffInToolCall() {
        return cutOff(AiMessage.from(PURGE_CUT_OFF));
    }

    // round 1 is a complete tool call, round 2 is cut off inside another tool call
    private static MockChatModel secondRoundCutOffModel() {
        return MockChatModel.thatResponds(AiMessage.from(PURGE_TMP)).thenResponds(cutOffInToolCall());
    }

    private static MockStreamingChatModel secondRoundCutOffStreamingModel() {
        return MockStreamingChatModel.thatResponds(AiMessage.from(PURGE_TMP)).thenResponds(cutOffInToolCall());
    }

    private static CompletableFuture<ChatResponse> start(TokenStream tokenStream) {
        CompletableFuture<ChatResponse> completed = new CompletableFuture<>();
        tokenStream
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(completed::complete)
                .onError(completed::completeExceptionally)
                .start();
        return completed;
    }

    private static void assertOnlyTheUserMessageIsInMemory(ChatMemory memory) {
        assertThat(memory.messages()).hasSize(1);
        assertThat(memory.messages().get(0)).isInstanceOf(UserMessage.class);
    }

    private static void assertOnlyTheFirstToolRoundIsInMemory(ChatMemory memory) {
        List<ChatMessage> messages = memory.messages();
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((AiMessage) messages.get(1)).toolExecutionRequests()).containsExactly(PURGE_TMP);
        assertThat(((ToolExecutionResultMessage) messages.get(2)).text()).isEqualTo("purged /tmp");
    }

    // Scenario: blocking, the first response is cut off inside a tool call.
    // Expected: the tool does not run, the caller gets an exception, memory holds only the user message.
    @Test
    void sync_service_should_refuse_a_tool_call_of_a_cut_off_response() {

        FileTools tools = new FileTools();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(MockChatModel.thatResponds(cutOffInToolCall()))
                .chatMemory(memory)
                .tools(tools)
                .build();

        assertThatThrownBy(() -> assistant.chat("Clean up the temp directory"))
                .isInstanceOf(LangChain4jException.class)
                .hasMessageContaining(REFUSAL);

        assertThat(tools.invocations).isEmpty();
        assertOnlyTheUserMessageIsInMemory(memory);
    }

    // Scenario: blocking, the cut off response has a tool call with complete arguments.
    // Expected: still refused, because a cut off response can also lack tool calls the model meant to make.
    @Test
    void sync_service_should_refuse_a_complete_tool_call_of_a_cut_off_response() {

        FileTools tools = new FileTools();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(MockChatModel.thatResponds(cutOff(AiMessage.from(PURGE_TMP))))
                .tools(tools)
                .build();

        assertThatThrownBy(() -> assistant.chat("Clean up the temp directory"))
                .isInstanceOf(LangChain4jException.class)
                .hasMessageContaining(REFUSAL);

        assertThat(tools.invocations).isEmpty();
    }

    // Scenario: blocking, round 1 is complete and round 2 is cut off inside a tool call.
    // Expected: round 1 runs, round 2 is refused, memory ends with the result of round 1 and no unanswered tool call.
    @Test
    void sync_service_should_refuse_a_tool_call_of_a_cut_off_second_round() {

        FileTools tools = new FileTools();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(secondRoundCutOffModel())
                .chatMemory(memory)
                .tools(tools)
                .build();

        assertThatThrownBy(() -> assistant.chat("Clean up the temp directory"))
                .isInstanceOf(LangChain4jException.class)
                .hasMessageContaining(REFUSAL);

        assertThat(tools.invocations).containsExactly("purge(/tmp)");
        assertOnlyTheFirstToolRoundIsInMemory(memory);
    }

    // Scenario: blocking, the response is cut off but requests no tool.
    // Expected: unchanged behavior, the partial answer is returned with the finish reason LENGTH.
    @Test
    void sync_service_should_still_return_a_cut_off_answer_without_tool_calls() {

        FileTools tools = new FileTools();
        ResultAssistant assistant = AiServices.builder(ResultAssistant.class)
                .chatModel(MockChatModel.thatResponds(cutOff(AiMessage.from("Once upon a"))))
                .tools(tools)
                .build();

        Result<String> result = assistant.chat("Tell me a story");

        assertThat(result.content()).isEqualTo("Once upon a");
        assertThat(result.finishReason()).isEqualTo(FinishReason.LENGTH);
        assertThat(tools.invocations).isEmpty();
    }

    // Scenario: blocking, a complete tool call whose response was not cut off.
    // Expected: unchanged behavior, the tool runs and the answer is returned.
    @Test
    void sync_service_should_still_run_a_tool_call_of_a_complete_response() {

        FileTools tools = new FileTools();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(MockChatModel.thatResponds(AiMessage.from(PURGE_TMP)).thenResponds("done"))
                .tools(tools)
                .build();

        assertThat(assistant.chat("Clean up the temp directory")).isEqualTo("done");
        assertThat(tools.invocations).containsExactly("purge(/tmp)");
    }

    // Scenario: CompletableFuture service, the first response is cut off inside a tool call.
    // Expected: the future fails with the exception, the tool does not run, memory holds only the user message.
    @Test
    void async_service_should_refuse_a_tool_call_of_a_cut_off_response() {

        FileTools tools = new FileTools();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(MockChatModel.thatResponds(cutOffInToolCall()))
                .chatMemory(memory)
                .tools(tools)
                .build();

        assertThat(assistant.chat("Clean up the temp directory"))
                .failsWithin(10, SECONDS)
                .withThrowableThat()
                .withCauseInstanceOf(LangChain4jException.class)
                .withMessageContaining(REFUSAL);

        assertThat(tools.invocations).isEmpty();
        assertOnlyTheUserMessageIsInMemory(memory);
    }

    // Scenario: CompletableFuture service, round 2 is cut off inside a tool call.
    // Expected: round 1 runs, round 2 is refused, memory ends with the result of round 1.
    @Test
    void async_service_should_refuse_a_tool_call_of_a_cut_off_second_round() {

        FileTools tools = new FileTools();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(secondRoundCutOffModel())
                .chatMemory(memory)
                .tools(tools)
                .build();

        assertThat(assistant.chat("Clean up the temp directory"))
                .failsWithin(10, SECONDS)
                .withThrowableThat()
                .withCauseInstanceOf(LangChain4jException.class)
                .withMessageContaining(REFUSAL);

        assertThat(tools.invocations).containsExactly("purge(/tmp)");
        assertOnlyTheFirstToolRoundIsInMemory(memory);
    }

    // Scenario: TokenStream service, the first response is cut off inside a tool call.
    // Expected: onError receives the exception, the tool does not run, memory holds only the user message.
    @Test
    void token_stream_service_should_refuse_a_tool_call_of_a_cut_off_response() {

        FileTools tools = new FileTools();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(MockStreamingChatModel.thatResponds(cutOffInToolCall()))
                .chatMemory(memory)
                .tools(tools)
                .build();

        assertThat(start(assistant.chat("Clean up the temp directory")))
                .failsWithin(10, SECONDS)
                .withThrowableThat()
                .withCauseInstanceOf(LangChain4jException.class)
                .withMessageContaining(REFUSAL);

        assertThat(tools.invocations).isEmpty();
        assertOnlyTheUserMessageIsInMemory(memory);
    }

    // Scenario: TokenStream service, round 2 is cut off inside a tool call.
    // Expected: round 1 runs, round 2 is refused, memory ends with the result of round 1.
    @Test
    void token_stream_service_should_refuse_a_tool_call_of_a_cut_off_second_round() {

        FileTools tools = new FileTools();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(secondRoundCutOffStreamingModel())
                .chatMemory(memory)
                .tools(tools)
                .build();

        assertThat(start(assistant.chat("Clean up the temp directory")))
                .failsWithin(10, SECONDS)
                .withThrowableThat()
                .withCauseInstanceOf(LangChain4jException.class)
                .withMessageContaining(REFUSAL);

        assertThat(tools.invocations).containsExactly("purge(/tmp)");
        assertOnlyTheFirstToolRoundIsInMemory(memory);
    }

    // Scenario: TokenStream service with concurrent tools, the first response is cut off inside a tool call.
    // Expected: the stream fails and memory holds only the user message. A tool the model completed before the cut
    // may already have started, so the tool invocations are not asserted.
    @Test
    void token_stream_service_with_concurrent_tools_should_refuse_a_cut_off_response() {

        FileTools tools = new FileTools();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(MockStreamingChatModel.thatResponds(cutOffInToolCall()))
                .chatMemory(memory)
                .tools(tools)
                .executeToolsConcurrently()
                .build();

        assertThat(start(assistant.chat("Clean up the temp directory")))
                .failsWithin(10, SECONDS)
                .withThrowableThat()
                .withCauseInstanceOf(LangChain4jException.class)
                .withMessageContaining(REFUSAL);

        assertOnlyTheUserMessageIsInMemory(memory);
    }

    // Scenario: reactive service, the first response is cut off inside a tool call.
    // Expected: the publisher terminates with the exception and memory holds only the user message. The reactive mode
    // starts a tool as soon as the model completes its call, before the finish reason is known, so the tool
    // invocations are not asserted.
    @Test
    void reactive_service_should_refuse_a_cut_off_response() {

        FileTools tools = new FileTools();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(MockStreamingChatModel.thatResponds(cutOffInToolCall()))
                .chatMemory(memory)
                .tools(tools)
                .build();

        EventCollector collector = new EventCollector();
        assistant.chat("Clean up the temp directory").subscribe(collector);

        assertThat(collector.terminated)
                .failsWithin(10, SECONDS)
                .withThrowableThat()
                .withCauseInstanceOf(LangChain4jException.class)
                .withMessageContaining(REFUSAL);

        assertOnlyTheUserMessageIsInMemory(memory);
    }

    // Scenario: reactive service, round 2 is cut off inside a tool call.
    // Expected: the publisher terminates with the exception and memory ends with the result of round 1, without the
    // unanswered tool call of round 2. The tool of round 2 may have been started, see the test above.
    @Test
    void reactive_service_should_refuse_a_cut_off_second_round() {

        FileTools tools = new FileTools();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(secondRoundCutOffStreamingModel())
                .chatMemory(memory)
                .tools(tools)
                .build();

        EventCollector collector = new EventCollector();
        assistant.chat("Clean up the temp directory").subscribe(collector);

        assertThat(collector.terminated)
                .failsWithin(10, SECONDS)
                .withThrowableThat()
                .withCauseInstanceOf(LangChain4jException.class)
                .withMessageContaining(REFUSAL);

        assertThat(tools.invocations).contains("purge(/tmp)");
        assertOnlyTheFirstToolRoundIsInMemory(memory);
    }
}
