package dev.langchain4j.model.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OllamaImageUrlTest {

    private static final String CHAT_RESPONSE =
            "{\"model\":\"llava\",\"message\":{\"role\":\"assistant\",\"content\":\"a cat\"},\"done\":true,"
                    + "\"done_reason\":\"stop\",\"prompt_eval_count\":1,\"eval_count\":1}";

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/cat.png", "http://localhost/cat.png", "file:///etc/hosts", "cat.png"})
    void chat_should_reject_images_given_by_url(String url) {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(CHAT_RESPONSE));
        ChatModel model = OllamaChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llava")
                .build();

        // when-then
        assertThatThrownBy(() -> model.chat(UserMessage.from(TextContent.from("What is this?"), ImageContent.from(url))))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage(ImageUtils.IMAGE_URL_NOT_SUPPORTED)
                .hasMessageNotContaining(url);
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void chat_should_reject_images_with_neither_base64_data_nor_a_url() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(CHAT_RESPONSE));
        ChatModel model = OllamaChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llava")
                .build();

        // when-then
        assertThatThrownBy(() -> model.chat(UserMessage.from(
                        TextContent.from("What is this?"),
                        ImageContent.from(Image.builder().mimeType("image/png").build()))))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The image has neither base64 data nor a URL");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void streaming_chat_should_reject_images_given_by_url() {

        // given
        MockHttpClient httpClient =
                MockHttpClient.thatAlwaysResponds(List.of(new ServerSentEvent(null, CHAT_RESPONSE)));
        StreamingChatModel model = OllamaStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llava")
                .build();
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from(
                        TextContent.from("What is this?"), ImageContent.from("https://example.com/cat.png")))
                .build();
        CompletingHandler handler = new CompletingHandler();

        // when-then
        assertThatThrownBy(() -> model.chat(request, handler))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage(ImageUtils.IMAGE_URL_NOT_SUPPORTED);
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void chat_should_send_images_read_from_local_files(@TempDir Path directory) throws Exception {

        // given
        Path image = Files.write(directory.resolve("cat.png"), new byte[] {1, 2, 3});
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(CHAT_RESPONSE));
        ChatModel model = OllamaChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llava")
                .build();

        // when
        model.chat(UserMessage.from(TextContent.from("What is this?"), ImageContent.from(image, "image/png")));

        // then
        assertThat(sentImages(httpClient))
                .containsExactly(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));
    }

    @SuppressWarnings("unchecked")
    private static List<String> sentImages(MockHttpClient httpClient) {
        Map<String, Object> body = Json.fromJson(httpClient.request().body(), Map.class);
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        return (List<String>) messages.get(0).get("images");
    }

    private static SuccessfulHttpResponse ok(String body) {
        return SuccessfulHttpResponse.builder().statusCode(200).body(body).build();
    }

    private static class CompletingHandler implements StreamingChatResponseHandler {

        final CompletableFuture<ChatResponse> response = new CompletableFuture<>();

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
    }
}
