package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.media.FetchedMedia;
import dev.langchain4j.data.media.MediaFetcher;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.googleai.BatchRequestResponse.Operation;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class GoogleAiGeminiMediaFetcherTest {

    private static final String MODEL_NAME = "gemini-test-model";
    private static final URI IMAGE_URL = URI.create("https://example.com/cat.png");
    private static final byte[] IMAGE_BYTES = "image bytes".getBytes(StandardCharsets.UTF_8);
    private static final String IMAGE_BASE64 = Base64.getEncoder().encodeToString(IMAGE_BYTES);

    private static final String GENERATE_CONTENT_RESPONSE = """
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
    void chat_model_downloads_image_urls_with_configured_media_fetcher() {
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(jsonResponse(GENERATE_CONTENT_RESPONSE));
        RecordingMediaFetcher mediaFetcher = new RecordingMediaFetcher();
        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("does not matter")
                .modelName(MODEL_NAME)
                .mediaFetcher(mediaFetcher)
                .build();

        ChatResponse response = model.chat(REQUEST_WITH_IMAGE_URL);

        assertThat(response.aiMessage().text()).isEqualTo("A cat");
        assertThat(mediaFetcher.fetched).containsExactly(IMAGE_URL);
        assertThat(mediaFetcher.fetchedAsync).isEmpty();
        assertThat(httpClient.request().body()).contains(IMAGE_BASE64).contains("image/png");
    }

    @Test
    void chat_model_rejects_image_urls_of_non_public_hosts_by_default() {
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(jsonResponse(GENERATE_CONTENT_RESPONSE));
        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("does not matter")
                .modelName(MODEL_NAME)
                .build();

        assertThatThrownBy(() -> model.chat(UserMessage.from(ImageContent.from("http://127.0.0.1/cat.png"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("non-public address");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void streaming_chat_model_downloads_image_urls_with_fetchAsync() throws Exception {
        MockHttpClient httpClient =
                MockHttpClient.thatAlwaysResponds(List.of(new ServerSentEvent("message", GENERATE_CONTENT_RESPONSE)));
        RecordingMediaFetcher mediaFetcher = new RecordingMediaFetcher();
        GoogleAiGeminiStreamingChatModel model = GoogleAiGeminiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("does not matter")
                .modelName(MODEL_NAME)
                .mediaFetcher(mediaFetcher)
                .build();

        CompletableFuture<ChatResponse> response = stream(model, REQUEST_WITH_IMAGE_URL);

        assertThat(response.get().aiMessage().text()).isEqualTo("A cat");
        assertThat(mediaFetcher.fetchedAsync).containsExactly(IMAGE_URL);
        assertThat(mediaFetcher.fetched).isEmpty();
        assertThat(httpClient.request().body()).contains(IMAGE_BASE64).contains("image/png");
    }

    @Test
    void streaming_chat_model_does_not_block_the_caller_while_downloading() throws Exception {
        MockHttpClient httpClient =
                MockHttpClient.thatAlwaysResponds(List.of(new ServerSentEvent("message", GENERATE_CONTENT_RESPONSE)));
        CompletableFuture<FetchedMedia> download = new CompletableFuture<>();
        MediaFetcher mediaFetcher = new MediaFetcher() {

            @Override
            public FetchedMedia fetch(URI url) {
                throw new AssertionError("fetch() must not be called");
            }

            @Override
            public CompletableFuture<FetchedMedia> fetchAsync(URI url) {
                return download;
            }
        };
        GoogleAiGeminiStreamingChatModel model = GoogleAiGeminiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("does not matter")
                .modelName(MODEL_NAME)
                .mediaFetcher(mediaFetcher)
                .build();

        CompletableFuture<ChatResponse> response = stream(model, REQUEST_WITH_IMAGE_URL);

        assertThat(response).isNotDone();
        assertThat(httpClient.requests()).isEmpty();

        download.complete(FetchedMedia.from(IMAGE_BYTES, "image/png"));

        assertThat(response.get().aiMessage().text()).isEqualTo("A cat");
        assertThat(httpClient.request().body()).contains(IMAGE_BASE64);
    }

    @Test
    void streaming_chat_model_fails_through_onError_when_media_fetcher_does_not_implement_fetchAsync() {
        MockHttpClient httpClient =
                MockHttpClient.thatAlwaysResponds(List.of(new ServerSentEvent("message", GENERATE_CONTENT_RESPONSE)));
        MediaFetcher syncOnlyMediaFetcher = url -> FetchedMedia.from(IMAGE_BYTES, "image/png");
        GoogleAiGeminiStreamingChatModel model = GoogleAiGeminiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("does not matter")
                .modelName(MODEL_NAME)
                .mediaFetcher(syncOnlyMediaFetcher)
                .build();

        CompletableFuture<ChatResponse> response = stream(model, REQUEST_WITH_IMAGE_URL);

        assertThat(response).failsWithin(Duration.ofSeconds(10))
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(AsyncNotSupportedException.class)
                .withMessageContaining("fetchAsync()");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void streaming_chat_model_without_image_urls_does_not_use_media_fetcher() throws Exception {
        MockHttpClient httpClient =
                MockHttpClient.thatAlwaysResponds(List.of(new ServerSentEvent("message", GENERATE_CONTENT_RESPONSE)));
        MediaFetcher syncOnlyMediaFetcher = url -> {
            throw new AssertionError("Unexpected download of " + url);
        };
        GoogleAiGeminiStreamingChatModel model = GoogleAiGeminiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("does not matter")
                .modelName(MODEL_NAME)
                .mediaFetcher(syncOnlyMediaFetcher)
                .build();

        CompletableFuture<ChatResponse> response = stream(
                model, ChatRequest.builder().messages(UserMessage.from("Hi")).build());

        assertThat(response.get().aiMessage().text()).isEqualTo("A cat");
    }

    @Test
    void streaming_chat_model_rejects_image_urls_of_non_public_hosts_by_default_through_onError() {
        MockHttpClient httpClient =
                MockHttpClient.thatAlwaysResponds(List.of(new ServerSentEvent("message", GENERATE_CONTENT_RESPONSE)));
        GoogleAiGeminiStreamingChatModel model = GoogleAiGeminiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("does not matter")
                .modelName(MODEL_NAME)
                .build();

        CompletableFuture<ChatResponse> response = stream(
                model,
                ChatRequest.builder()
                        .messages(UserMessage.from(ImageContent.from("http://127.0.0.1/cat.png")))
                        .build());

        assertThat(response).failsWithin(Duration.ofSeconds(10))
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(InvalidRequestException.class)
                .withMessageContaining("non-public address");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void batch_chat_model_downloads_image_urls_with_configured_media_fetcher() {
        Operation<GeminiGenerateContentResponse> pending =
                new Operation<>("batches/test-1", Map.of("state", "PENDING"), false, null, null);
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(jsonResponse(Json.toJson(pending)));
        RecordingMediaFetcher mediaFetcher = new RecordingMediaFetcher();
        GoogleAiGeminiBatchChatModel model = GoogleAiGeminiBatchChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("does not matter")
                .modelName(MODEL_NAME)
                .mediaFetcher(mediaFetcher)
                .build();

        model.submit(new BatchRequest<>(List.of(REQUEST_WITH_IMAGE_URL)));

        assertThat(mediaFetcher.fetched).containsExactly(IMAGE_URL);
        assertThat(httpClient.request().body()).contains(IMAGE_BASE64);
    }

    @Test
    void token_count_estimator_downloads_image_urls_with_configured_media_fetcher() {
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(jsonResponse("{\"totalTokens\": 42}"));
        RecordingMediaFetcher mediaFetcher = new RecordingMediaFetcher();
        GoogleAiGeminiTokenCountEstimator estimator = GoogleAiGeminiTokenCountEstimator.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("does not matter")
                .modelName(MODEL_NAME)
                .mediaFetcher(mediaFetcher)
                .build();

        int tokenCount = estimator.estimateTokenCountInMessage(UserMessage.from(ImageContent.from(IMAGE_URL)));

        assertThat(tokenCount).isEqualTo(42);
        assertThat(mediaFetcher.fetched).containsExactly(IMAGE_URL);
        assertThat(httpClient.request().body()).contains(IMAGE_BASE64);
    }

    @Test
    void caches_download_image_urls_with_configured_media_fetcher() {
        MockHttpClient httpClient =
                MockHttpClient.thatAlwaysResponds(jsonResponse("{\"name\": \"cachedContents/abc123\"}"));
        RecordingMediaFetcher mediaFetcher = new RecordingMediaFetcher();
        GeminiCaches caches = GeminiCaches.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("does not matter")
                .mediaFetcher(mediaFetcher)
                .build();

        caches.createCache(MODEL_NAME, List.of(UserMessage.from(ImageContent.from(IMAGE_URL))));

        assertThat(mediaFetcher.fetched).containsExactly(IMAGE_URL);
        assertThat(httpClient.request().body()).contains(IMAGE_BASE64);
    }

    private static CompletableFuture<ChatResponse> stream(GoogleAiGeminiStreamingChatModel model, ChatRequest request) {
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        model.chat(request, new StreamingChatResponseHandler() {

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
        return response;
    }

    private static SuccessfulHttpResponse jsonResponse(String body) {
        return SuccessfulHttpResponse.builder().statusCode(200).body(body).build();
    }

    private static final class RecordingMediaFetcher implements MediaFetcher {

        private final List<URI> fetched = new CopyOnWriteArrayList<>();
        private final List<URI> fetchedAsync = new CopyOnWriteArrayList<>();

        @Override
        public FetchedMedia fetch(URI url) {
            fetched.add(url);
            return FetchedMedia.from(IMAGE_BYTES, "image/webp");
        }

        @Override
        public CompletableFuture<FetchedMedia> fetchAsync(URI url) {
            fetchedAsync.add(url);
            return CompletableFuture.completedFuture(FetchedMedia.from(IMAGE_BYTES, "image/webp"));
        }
    }
}
