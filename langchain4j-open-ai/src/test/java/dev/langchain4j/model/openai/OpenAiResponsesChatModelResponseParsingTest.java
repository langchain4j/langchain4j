package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

/**
 * Covers how the non-streaming response body of the Responses API is assembled
 * into the {@code AiMessage} text.
 */
class OpenAiResponsesChatModelResponseParsingTest {

    private static ChatResponse chatWith(String outputJson) {
        String responseJson = """
                {
                  "id": "resp_1",
                  "model": "gpt-5.6",
                  "object": "response",
                  "status": "completed",
                  "output": %s
                }
                """.formatted(outputJson);

        MockHttpClient mockHttpClient = new MockHttpClient(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(responseJson)
                .build());

        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("http://localhost")
                .apiKey("dummy")
                .modelName("gpt-5.6")
                .build();

        return model.chat(
                ChatRequest.builder().messages(UserMessage.from("Hello")).build());
    }

    @Test
    void should_surface_only_the_final_answer_message_item() {
        ChatResponse response = chatWith("""
                [
                  {
                    "id": "msg_1",
                    "type": "message",
                    "phase": "commentary",
                    "role": "assistant",
                    "status": "completed",
                    "content": [{"type": "output_text", "text": "Let me check that for you."}]
                  },
                  {
                    "id": "msg_2",
                    "type": "message",
                    "phase": "final_answer",
                    "role": "assistant",
                    "status": "completed",
                    "content": [{"type": "output_text", "text": "Your balance is $40."}]
                  }
                ]""");

        assertThat(response.aiMessage().text()).isEqualTo("Your balance is $40.");
    }

    @Test
    void should_concatenate_message_items_when_they_carry_no_phase() {
        ChatResponse response = chatWith("""
                [
                  {
                    "id": "msg_1",
                    "type": "message",
                    "role": "assistant",
                    "content": [{"type": "output_text", "text": "Hello"}]
                  },
                  {
                    "id": "msg_2",
                    "type": "message",
                    "role": "assistant",
                    "content": [{"type": "output_text", "text": ", world"}]
                  }
                ]""");

        assertThat(response.aiMessage().text()).isEqualTo("Hello, world");
    }
}
