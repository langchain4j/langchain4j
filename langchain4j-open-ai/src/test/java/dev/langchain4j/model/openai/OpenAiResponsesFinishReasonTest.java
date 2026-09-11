package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiResponsesFinishReasonTest {

    private static String incompleteResponseJson(String incompleteDetails) {
        return """
                {
                  "id": "resp_incomplete",
                  "model": "gpt-5.4-mini",
                  "object": "response",
                  "status": "incomplete",
                  %s
                  "output": [
                    {
                      "id": "msg_1",
                      "type": "message",
                      "role": "assistant",
                      "content": [{"type": "output_text", "text": "Partial"}]
                    }
                  ]
                }
                """.formatted(incompleteDetails);
    }

    private static ChatResponse chatWith(String responseJson) {
        MockHttpClient mockHttpClient = new MockHttpClient(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(responseJson)
                .build());

        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("http://localhost")
                .apiKey("dummy")
                .modelName("gpt-5.4-mini")
                .build();

        return model.chat(
                ChatRequest.builder().messages(UserMessage.from("Hello")).build());
    }

    @Test
    void should_report_content_filter_when_response_is_incomplete_because_of_the_content_filter() {
        ChatResponse response =
                chatWith(incompleteResponseJson("\"incomplete_details\": {\"reason\": \"content_filter\"},"));

        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.CONTENT_FILTER);
    }

    @Test
    void should_report_length_when_response_is_incomplete_because_of_the_token_limit() {
        ChatResponse response =
                chatWith(incompleteResponseJson("\"incomplete_details\": {\"reason\": \"max_output_tokens\"},"));

        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.LENGTH);
    }

    @Test
    void should_report_length_when_the_incomplete_reason_is_not_recognised() {
        ChatResponse response =
                chatWith(incompleteResponseJson("\"incomplete_details\": {\"reason\": \"future_reason\"},"));

        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.LENGTH);
    }

    @Test
    void should_report_length_when_response_is_incomplete_without_details() {
        ChatResponse response = chatWith(incompleteResponseJson(""));

        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.LENGTH);
    }

    @Test
    void should_report_content_filter_when_stream_ends_incomplete_because_of_the_content_filter() {
        ServerSentEvent incompleteEvent = new ServerSentEvent(
                null,
                "{\"type\":\"response.incomplete\",\"response\":"
                        + incompleteResponseJson("\"incomplete_details\": {\"reason\": \"content_filter\"},")
                        + "}");

        OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey("dummy")
                .httpClientBuilder(
                        new MockHttpClientBuilder(MockHttpClient.thatAlwaysResponds(List.of(incompleteEvent))))
                .modelName("gpt-5.4-mini")
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("Hello", handler);

        assertThat(handler.get().metadata().finishReason()).isEqualTo(FinishReason.CONTENT_FILTER);
    }
}
