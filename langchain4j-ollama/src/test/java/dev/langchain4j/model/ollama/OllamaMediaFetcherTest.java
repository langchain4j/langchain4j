package dev.langchain4j.model.ollama;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.media.FetchedMedia;
import dev.langchain4j.data.media.MediaFetcher;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.exception.InvalidRequestException;
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
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OllamaMediaFetcherTest {

    private static final URI IMAGE_URL = URI.create("https://example.com/cat.png");
    private static final byte[] IMAGE_BYTES = {1, 2, 3};
    private static final String IMAGE_BASE64 = Base64.getEncoder().encodeToString(IMAGE_BYTES);

    private static final MediaFetcher SYNC_ONLY_FETCHER = url -> FetchedMedia.from(IMAGE_BYTES, "image/png");

    private static final String CHAT_RESPONSE =
            "{\"model\":\"llava\",\"message\":{\"role\":\"assistant\",\"content\":\"a cat\"},\"done\":true,"
                    + "\"done_reason\":\"stop\",\"prompt_eval_count\":1,\"eval_count\":1}";
    private static final String DECISION_RESPONSE =
            "{\"model\": \"clef\", \"answers\": {\"cat\": {\"type\": \"noul\", \"noul\": 0.9}}}";

    @Test
    void chat_should_download_images_through_the_configured_media_fetcher() {
        RecordingMediaFetcher mediaFetcher = new RecordingMediaFetcher();
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(CHAT_RESPONSE));
        ChatModel model = OllamaChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llava")
                .mediaFetcher(mediaFetcher)
                .build();

        model.chat(UserMessage.from(TextContent.from("What is this?"), ImageContent.from(IMAGE_URL)));

        assertThat(sentImages(httpClient)).containsExactly(IMAGE_BASE64);
        assertThat(mediaFetcher.fetched).containsExactly(IMAGE_URL);
        assertThat(mediaFetcher.fetchedAsync).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "/home/me/cat.png", "cat.png"})
    void chat_should_not_read_local_files_by_default(String localFile) {
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(CHAT_RESPONSE));
        ChatModel model = OllamaChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llava")
                .build();

        assertThatThrownBy(() -> model.chat(UserMessage.from(ImageContent.from(localFile))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("from(Path");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void streaming_should_download_images_with_fetchAsync() throws Exception {
        RecordingMediaFetcher mediaFetcher = new RecordingMediaFetcher();
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(List.of(new ServerSentEvent(null, CHAT_RESPONSE)));
        StreamingChatModel model = streamingModel(httpClient, mediaFetcher);

        CompletingHandler handler = new CompletingHandler();
        model.chat(
                ChatRequest.builder()
                        .messages(UserMessage.from(TextContent.from("What is this?"), ImageContent.from(IMAGE_URL)))
                        .build(),
                handler);

        assertThat(handler.response.get(10, SECONDS).aiMessage().text()).isEqualTo("a cat");
        assertThat(sentImages(httpClient)).containsExactly(IMAGE_BASE64);
        assertThat(mediaFetcher.fetchedAsync).containsExactly(IMAGE_URL);
        assertThat(mediaFetcher.fetched).isEmpty();
    }

    @Test
    void streaming_should_report_to_onError_when_the_media_fetcher_cannot_download_asynchronously() {
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(List.of(new ServerSentEvent(null, CHAT_RESPONSE)));
        StreamingChatModel model = streamingModel(httpClient, SYNC_ONLY_FETCHER);

        CompletingHandler handler = new CompletingHandler();
        model.chat(
                ChatRequest.builder()
                        .messages(UserMessage.from(ImageContent.from(IMAGE_URL)))
                        .build(),
                handler);

        assertThat(handler.response)
                .failsWithin(10, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(AsyncNotSupportedException.class)
                .withMessageContaining("fetchAsync()");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void streaming_should_not_call_the_media_fetcher_without_image_urls() throws Exception {
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(List.of(new ServerSentEvent(null, CHAT_RESPONSE)));
        StreamingChatModel model = streamingModel(httpClient, SYNC_ONLY_FETCHER);

        CompletingHandler handler = new CompletingHandler();
        model.chat(
                ChatRequest.builder()
                        .messages(UserMessage.from(TextContent.from("hi"), ImageContent.from("aGVsbG8=", "image/png")))
                        .build(),
                handler);

        assertThat(handler.response.get(10, SECONDS).aiMessage().text()).isEqualTo("a cat");
        assertThat(sentImages(httpClient)).containsExactly("aGVsbG8=");
    }

    @Test
    void streaming_should_report_the_rejection_of_a_local_file_to_onError() {
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(List.of(new ServerSentEvent(null, CHAT_RESPONSE)));
        StreamingChatModel model = OllamaStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llava")
                .build();

        CompletingHandler handler = new CompletingHandler();
        model.chat(
                ChatRequest.builder()
                        .messages(UserMessage.from(ImageContent.from(URI.create("file:///etc/passwd"))))
                        .build(),
                handler);

        assertThat(handler.response)
                .failsWithin(10, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(InvalidRequestException.class)
                .withMessageContaining("from(Path");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void decide_should_download_images_through_the_configured_media_fetcher() {
        RecordingMediaFetcher mediaFetcher = new RecordingMediaFetcher();
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(DECISION_RESPONSE));

        decisionModel(httpClient, mediaFetcher).decide(decisionRequest(ImageContent.from(IMAGE_URL)));

        assertThat(sentImages(httpClient)).containsExactly(IMAGE_BASE64);
        assertThat(mediaFetcher.fetched).containsExactly(IMAGE_URL);
        assertThat(mediaFetcher.fetchedAsync).isEmpty();
    }

    @Test
    void decide_should_not_read_local_files_by_default() {
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(DECISION_RESPONSE));
        DecisionModel model = OllamaDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:11434")
                .modelName("clef")
                .build();
        DecisionRequest request = decisionRequest(ImageContent.from(URI.create("file:///etc/passwd")));

        assertThatThrownBy(() -> model.decide(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("from(Path");
        assertThat(model.decideAsync(request))
                .failsWithin(10, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(InvalidRequestException.class)
                .withMessageContaining("from(Path");
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void decideAsync_should_download_images_with_fetchAsync() {
        RecordingMediaFetcher mediaFetcher = new RecordingMediaFetcher();
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(DECISION_RESPONSE));

        CompletableFuture<DecisionResponse> response =
                decisionModel(httpClient, mediaFetcher).decideAsync(decisionRequest(ImageContent.from(IMAGE_URL)));

        assertThat(response).succeedsWithin(10, SECONDS);
        assertThat(sentImages(httpClient)).containsExactly(IMAGE_BASE64);
        assertThat(mediaFetcher.fetchedAsync).containsExactly(IMAGE_URL);
        assertThat(mediaFetcher.fetched).isEmpty();
    }

    @Test
    void decideAsync_should_download_the_images_of_fields_and_conversations_with_fetchAsync() {
        URI otherUrl = URI.create("https://example.com/dog.png");
        RecordingMediaFetcher mediaFetcher = new RecordingMediaFetcher();
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(DECISION_RESPONSE));
        OllamaDecisionModel model = decisionModel(httpClient, mediaFetcher);

        assertThat(model.decideAsync(DecisionRequest.builder()
                        .inputFields(Map.of("photo", ImageContent.from(IMAGE_URL)))
                        .question("cat", YesNoQuestion.from("Is there a cat?"))
                        .build()))
                .succeedsWithin(10, SECONDS);
        assertThat(model.decideAsync(DecisionRequest.builder()
                        .inputMessages(List.of(
                                UserMessage.from(TextContent.from("Look"), ImageContent.from(otherUrl)),
                                AiMessage.from("A dog.")))
                        .question("cat", YesNoQuestion.from("Is there a cat?"))
                        .build()))
                .succeedsWithin(10, SECONDS);

        assertThat(mediaFetcher.fetchedAsync).containsExactly(IMAGE_URL, otherUrl);
        assertThat(mediaFetcher.fetched).isEmpty();
    }

    @Test
    void decideAsync_should_fail_through_the_future_when_the_media_fetcher_cannot_download_asynchronously() {
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(DECISION_RESPONSE));

        CompletableFuture<DecisionResponse> response = decisionModel(httpClient, SYNC_ONLY_FETCHER)
                .decideAsync(decisionRequest(ImageContent.from(IMAGE_URL)));

        assertThat(response)
                .failsWithin(10, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(AsyncNotSupportedException.class);
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void decideAsync_should_not_call_the_media_fetcher_without_image_urls() {
        MockHttpClient httpClient = MockHttpClient.thatAlwaysResponds(ok(DECISION_RESPONSE));

        CompletableFuture<DecisionResponse> response = decisionModel(httpClient, SYNC_ONLY_FETCHER)
                .decideAsync(decisionRequest(ImageContent.from("aGVsbG8=", "image/png")));

        assertThat(response).succeedsWithin(10, SECONDS);
    }

    private static StreamingChatModel streamingModel(MockHttpClient httpClient, MediaFetcher mediaFetcher) {
        return OllamaStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llava")
                .mediaFetcher(mediaFetcher)
                .build();
    }

    private static OllamaDecisionModel decisionModel(MockHttpClient httpClient, MediaFetcher mediaFetcher) {
        return OllamaDecisionModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost:11434")
                .modelName("clef")
                .mediaFetcher(mediaFetcher)
                .build();
    }

    private static DecisionRequest decisionRequest(ImageContent image) {
        return DecisionRequest.builder()
                .inputContents(List.of(TextContent.from("A photo."), image))
                .question("cat", YesNoQuestion.from("Is there a cat?"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<String> sentImages(MockHttpClient httpClient) {
        Map<String, Object> body = Json.fromJson(httpClient.request().body(), Map.class);
        if (body.get("images") != null) {
            return (List<String>) body.get("images");
        }
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        return (List<String>) messages.get(0).get("images");
    }

    private static SuccessfulHttpResponse ok(String body) {
        return SuccessfulHttpResponse.builder().statusCode(200).body(body).build();
    }

    /**
     * Returns {@link #IMAGE_BYTES} for every URL and records whether it was asked with {@link #fetch(URI)} or
     * {@link #fetchAsync(URI)}.
     */
    private static class RecordingMediaFetcher implements MediaFetcher {

        final List<URI> fetched = new CopyOnWriteArrayList<>();
        final List<URI> fetchedAsync = new CopyOnWriteArrayList<>();

        @Override
        public FetchedMedia fetch(URI url) {
            fetched.add(url);
            return FetchedMedia.from(IMAGE_BYTES, "image/png");
        }

        @Override
        public CompletableFuture<FetchedMedia> fetchAsync(URI url) {
            fetchedAsync.add(url);
            return CompletableFuture.supplyAsync(() -> FetchedMedia.from(IMAGE_BYTES, "image/png"));
        }
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
