package dev.langchain4j.model.googleai;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class GoogleAiGeminiImageUrlTest {

    private static final String IMAGE_URL = "https://example.com/cat.png";

    private static final String GENERATE_CONTENT_RESPONSE =
            """
            {
              "candidates": [
                {
                  "content": { "role": "model", "parts": [ { "text": "A cat" } ] },
                  "finishReason": "STOP"
                }
              ],
              "usageMetadata": { "promptTokenCount": 10, "candidatesTokenCount": 2, "totalTokenCount": 12 }
            }
            """;

    private static final ChatRequest REQUEST_WITH_IMAGE_URL = ChatRequest.builder()
            .messages(UserMessage.from(TextContent.from("What is in the image?"), ImageContent.from(IMAGE_URL)))
            .build();

    @Test
    void chat_model_should_pass_image_urls_on_to_gemini() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(GENERATE_CONTENT_RESPONSE)
                .build());
        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("does not matter")
                .modelName("gemini-test-model")
                .build();

        // when
        ChatResponse response = model.chat(REQUEST_WITH_IMAGE_URL);

        // then
        assertThat(response.aiMessage().text()).isEqualTo("A cat");
        assertThat(httpClient.requests()).hasSize(1);
        assertImagePassedOn(httpClient.request().body());
    }

    @Test
    void streaming_chat_model_should_pass_image_urls_on_to_gemini() throws Exception {

        // given
        MockHttpClient httpClient =
                MockHttpClient.thatAlwaysResponds(List.of(new ServerSentEvent("message", GENERATE_CONTENT_RESPONSE)));
        GoogleAiGeminiStreamingChatModel model = GoogleAiGeminiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("does not matter")
                .modelName("gemini-test-model")
                .build();
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();

        // when
        model.chat(REQUEST_WITH_IMAGE_URL, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                response.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                response.completeExceptionally(error);
            }
        });

        // then
        assertThat(response.get(10, SECONDS).aiMessage().text()).isEqualTo("A cat");
        assertImagePassedOn(httpClient.request().body());
    }

    private static void assertImagePassedOn(String requestBody) throws Exception {
        JsonNode parts = new ObjectMapper().readTree(requestBody).path("contents").get(0).path("parts");
        assertThat(parts.get(1).path("inlineData").isMissingNode()).isTrue();
        assertThat(parts.get(1).path("fileData").path("fileUri").asText()).isEqualTo(IMAGE_URL);
        assertThat(parts.get(1).path("fileData").path("mimeType").asText()).isEqualTo("image/png");
    }
}
