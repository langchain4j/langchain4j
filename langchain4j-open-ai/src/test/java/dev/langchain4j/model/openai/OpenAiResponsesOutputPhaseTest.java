package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for issue #6399: when the Responses API output contains both
 * {@code phase=commentary} and {@code phase=final_answer} message items,
 * {@code AiMessage.text()} must prefer the {@code final_answer} text instead of
 * concatenating both.
 */
class OpenAiResponsesOutputPhaseTest {

    private static final String MODEL_NAME = "gpt-5.4-mini";

    @Test
    void should_prefer_final_answer_phase_over_commentary() {
        // Given
        String responseBody = responseBody(
                messageItem("commentary", "Let me work this out."), messageItem("final_answer", "The answer is 42."));

        // When
        ChatResponse response = chat(responseBody);

        // Then
        assertThat(response.aiMessage().text()).isEqualTo("The answer is 42.");
    }

    @Test
    void should_not_duplicate_text_shared_by_commentary_and_final_answer() {
        // Given
        String responseBody = responseBody(messageItem("commentary", "42"), messageItem("final_answer", "42"));

        // When
        ChatResponse response = chat(responseBody);

        // Then
        assertThat(response.aiMessage().text()).isEqualTo("42");
    }

    @Test
    void should_concatenate_all_message_items_when_no_phase_is_present() {
        // Given
        String responseBody = responseBody(messageItem(null, "Hello"), messageItem(null, " world"));

        // When
        ChatResponse response = chat(responseBody);

        // Then
        assertThat(response.aiMessage().text()).isEqualTo("Hello world");
    }

    @Test
    void should_keep_commentary_text_when_no_final_answer_is_present() {
        // Given
        String responseBody = responseBody(messageItem("commentary", "Just a commentary."));

        // When
        ChatResponse response = chat(responseBody);

        // Then
        assertThat(response.aiMessage().text()).isEqualTo("Just a commentary.");
    }

    @Test
    void should_concatenate_all_final_answer_messages_in_order_and_ignore_commentary() {
        // Given
        String responseBody = responseBody(
                messageItem("commentary", "Let me work this out."),
                messageItem("final_answer", "First part. "),
                messageItem("commentary", "Still thinking."),
                messageItem("final_answer", "Second part."));

        // When
        ChatResponse response = chat(responseBody);

        // Then
        assertThat(response.aiMessage().text()).isEqualTo("First part. Second part.");
    }

    @Test
    void should_ignore_phase_less_message_when_final_answer_is_present() {
        // Given
        String responseBody =
                responseBody(messageItem(null, "Ignore me."), messageItem("final_answer", "The answer is 42."));

        // When
        ChatResponse response = chat(responseBody);

        // Then
        assertThat(response.aiMessage().text()).isEqualTo("The answer is 42.");
    }

    @Test
    void should_concatenate_multiple_output_text_items_within_a_single_final_answer_message() {
        // Given
        String responseBody = responseBody(messageItem("final_answer", "The answer ", "is 42."));

        // When
        ChatResponse response = chat(responseBody);

        // Then
        assertThat(response.aiMessage().text()).isEqualTo("The answer is 42.");
    }

    @Test
    void should_prefer_final_answer_phase_in_streaming_response_completed() {
        // Given
        String responseJson = responseBody(
                messageItem("commentary", "Let me work this out."), messageItem("final_answer", "The answer is 42."));
        List<ServerSentEvent> events = List.of(
                new ServerSentEvent(null, "{\"type\":\"response.output_text.delta\",\"delta\":\"The answer is 42.\"}"),
                new ServerSentEvent(null, "{\"type\":\"response.completed\",\"response\":" + responseJson + "}"),
                new ServerSentEvent(null, "[DONE]"));
        MockHttpClient mockHttpClient = new MockHttpClient(events);

        OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName(MODEL_NAME)
                .build();

        // When
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("Hello", handler);

        // Then
        assertThat(handler.get().aiMessage().text()).isEqualTo("The answer is 42.");
    }

    private static ChatResponse chat(String responseBody) {
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("Content-Type", List.of("application/json")))
                .body(responseBody)
                .build());

        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName(MODEL_NAME)
                .build();

        return model.chat(UserMessage.from("Hello"));
    }

    private static String responseBody(String... messageItems) {
        return """
                {
                  "id": "resp_1",
                  "model": "%s",
                  "status": "completed",
                  "output": [%s]
                }""".formatted(MODEL_NAME, String.join(",", messageItems));
    }

    private static String messageItem(String phase, String... texts) {
        String phaseField = phase == null ? "" : "\"phase\": \"" + phase + "\",";
        StringBuilder contentBuilder = new StringBuilder();
        for (String text : texts) {
            if (!contentBuilder.isEmpty()) {
                contentBuilder.append(", ");
            }
            contentBuilder
                    .append("{\"type\": \"output_text\", \"text\": \"")
                    .append(text)
                    .append("\"}");
        }
        return """
                {
                  "type": "message",
                  "role": "assistant",
                  %s
                  "content": [%s]
                }""".formatted(phaseField, contentBuilder);
    }
}
