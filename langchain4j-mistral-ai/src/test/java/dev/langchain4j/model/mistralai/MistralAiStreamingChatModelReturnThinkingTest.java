package dev.langchain4j.model.mistralai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class MistralAiStreamingChatModelReturnThinkingTest {

    private static final String MODEL = "magistral-medium-latest";

    @Test
    void should_return_thinking_when_returnThinking_is_true() {
        // given
        List<ServerSentEvent> events = List.of(
                thinkingEvent("Let me think"),
                thinkingEvent(" about this problem..."),
                textEvent("The answer"),
                textEvent(" is 42."),
                finishEvent("stop"),
                doneEvent());

        StreamingChatModel model = createModel(events, true);

        // when
        ChatResponse response = chat(model, "What is the answer?");

        // then
        assertThat(response.aiMessage().text()).isEqualTo("The answer is 42.");
        assertThat(response.aiMessage().thinking()).isEqualTo("Let me think about this problem...");
    }

    @Test
    void should_NOT_return_thinking_when_returnThinking_is_false() {
        // given
        List<ServerSentEvent> events = List.of(
                thinkingEvent("Let me think about this problem..."),
                textEvent("The answer is 42."),
                finishEvent("stop"),
                doneEvent());

        StreamingChatModel model = createModel(events, false);

        // when
        ChatResponse response = chat(model, "What is the answer?");

        // then
        assertThat(response.aiMessage().text()).isEqualTo("The answer is 42.");
        assertThat(response.aiMessage().thinking()).isNull();
    }

    @Test
    void should_handle_empty_thinking_array() {
        // given - real Magistral response: text, empty thinking, text
        List<ServerSentEvent> events = List.of(
                textEvent("Let's denote the cost of the ball as x dollars."),
                emptyThinkingEvent(),
                textEvent("Therefore, the ball costs $0.05."),
                finishEvent("stop"),
                doneEvent());

        StreamingChatModel model = createModel(events, true);

        // when
        ChatResponse response = chat(model, "How much does the ball cost?");

        // then - text blocks concatenated, empty thinking array results in null
        assertThat(response.aiMessage().text())
                .isEqualTo("Let's denote the cost of the ball as x dollars.Therefore, the ball costs $0.05.");
        assertThat(response.aiMessage().thinking()).isNull();
    }

    @Test
    void should_handle_thinking_with_tool_calls() {
        // given - thinking content with tool calls
        List<ServerSentEvent> events = List.of(
                thinkingEvent("The user wants to know the weather"),
                thinkingEvent(" in Munich. I need to call the getWeather function."),
                toolCallEvent("ggLXfzi8o", "getWeather", "{\"city\": \"Munich\"}"),
                finishEvent("tool_calls"),
                doneEvent());

        StreamingChatModel model = createModel(events, true);

        // when
        ChatResponse response = chat(model, "What is the weather in Munich?");

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.thinking())
                .isEqualTo("The user wants to know the weather in Munich. I need to call the getWeather function.");
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        assertThat(aiMessage.toolExecutionRequests().get(0).id()).isEqualTo("ggLXfzi8o");
        assertThat(aiMessage.toolExecutionRequests().get(0).name()).isEqualTo("getWeather");
        assertThat(aiMessage.toolExecutionRequests().get(0).arguments()).isEqualTo("{\"city\": \"Munich\"}");
    }

    @Test
    void should_handle_response_without_thinking() {
        // given - response with only text content, no thinking
        List<ServerSentEvent> events =
                List.of(textEvent("The answer"), textEvent(" is"), textEvent(" 42."), finishEvent("stop"), doneEvent());

        StreamingChatModel model = createModel(events, true);

        // when
        ChatResponse response = chat(model, "What is the answer?");

        // then - thinking should be null when not present in response
        assertThat(response.aiMessage().text()).isEqualTo("The answer is 42.");
        assertThat(response.aiMessage().thinking()).isNull();
    }

    @Test
    void should_concatenate_multiple_thinking_blocks() {
        // given - response with multiple thinking chunks streamed separately
        List<ServerSentEvent> events = List.of(
                thinkingEvent("First"),
                thinkingEvent(" thought..."),
                thinkingEvent("Second"),
                thinkingEvent(" thought..."),
                textEvent("The answer is 42."),
                finishEvent("stop"),
                doneEvent());

        StreamingChatModel model = createModel(events, true);

        // when
        ChatResponse response = chat(model, "What is the answer?");

        // then - multiple thinking blocks should be concatenated
        assertThat(response.aiMessage().text()).isEqualTo("The answer is 42.");
        assertThat(response.aiMessage().thinking()).isEqualTo("First thought...Second thought...");
    }

    @Test
    void should_NOT_return_thinking_with_tool_calls_when_returnThinking_is_false() {
        // given - thinking content with tool calls, but returnThinking is false
        List<ServerSentEvent> events = List.of(
                thinkingEvent("The user wants to know the weather in Munich."),
                toolCallEvent("ggLXfzi8o", "getWeather", "{\"city\": \"Munich\"}"),
                finishEvent("tool_calls"),
                doneEvent());

        StreamingChatModel model = createModel(events, false);

        // when
        ChatResponse response = chat(model, "What is the weather in Munich?");

        // then - thinking should be null, but tool calls should still be present
        assertThat(response.aiMessage().thinking()).isNull();
        assertThat(response.aiMessage().toolExecutionRequests()).hasSize(1);
        assertThat(response.aiMessage().toolExecutionRequests().get(0).id()).isEqualTo("ggLXfzi8o");
        assertThat(response.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("getWeather");
    }

    // -- Helper methods --

    private static StreamingChatModel createModel(List<ServerSentEvent> events, boolean returnThinking) {
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(events);
        return MistralAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName(MODEL)
                .returnThinking(returnThinking)
                .build();
    }

    private static ChatResponse chat(StreamingChatModel model, String message) {
        var handler = new TestStreamingChatResponseHandler();
        model.chat(message, handler);
        return handler.get();
    }

    private static ServerSentEvent thinkingEvent(String text) {
        String escaped = text.replace("\"", "\\\"");
        return event(
                """
                {"id":"abc123","model":"%s","choices":[{"index":0,"delta":{"content":[{"type":"thinking","thinking":[{"type":"text","text":"%s"}]}]}}]}"""
                        .formatted(MODEL, escaped));
    }

    private static ServerSentEvent emptyThinkingEvent() {
        return event(
                """
                {"id":"abc123","model":"%s","choices":[{"index":0,"delta":{"content":[{"type":"thinking","thinking":[]}]}}]}"""
                        .formatted(MODEL));
    }

    private static ServerSentEvent textEvent(String text) {
        String escaped = text.replace("\"", "\\\"");
        return event(
                """
                {"id":"abc123","model":"%s","choices":[{"index":0,"delta":{"content":[{"type":"text","text":"%s"}]}}]}"""
                        .formatted(MODEL, escaped));
    }

    private static ServerSentEvent toolCallEvent(String id, String name, String arguments) {
        String escapedArgs = arguments.replace("\"", "\\\"");
        return event(
                """
                {"id":"abc123","model":"%s","choices":[{"index":0,"delta":{"tool_calls":[{"id":"%s","type":"function","function":{"name":"%s","arguments":"%s"}}]}}]}"""
                        .formatted(MODEL, id, name, escapedArgs));
    }

    private static ServerSentEvent finishEvent(String reason) {
        return event(
                """
                {"id":"abc123","model":"%s","choices":[{"index":0,"delta":{},"finish_reason":"%s"}],"usage":{"prompt_tokens":10,"completion_tokens":20,"total_tokens":30,"num_cached_tokens":0}}"""
                        .formatted(MODEL, reason));
    }

    private static ServerSentEvent doneEvent() {
        return new ServerSentEvent(null, "[DONE]");
    }

    private static ServerSentEvent event(String data) {
        return new ServerSentEvent(null, data);
    }
}
