package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GeminiServiceTest {
    private static final String TEST_API_KEY = "test-api-key";
    private static final String TEST_BASE_URL = "https://test.googleapis.com/v1beta";
    private static final String TEST_MODEL_NAME = "gemini-pro";

    @Test
    void shouldThrownWhenApiKeyIsMissing() {
        assertThatThrownBy(() ->
                        new GeminiService(null, /* apiKey= */ null, TEST_BASE_URL, false, false, false, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("apiKey cannot be null or blank");
    }

    @Nested
    class GenerateContentTest {
        @Test
        void shouldSendGenerateContentRequest() {
            // Given
            GeminiGenerateContentResponse expectedResponse = createGenerateContentResponse("Hello, world!");
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            GeminiService subject = createService(mockHttpClient);

            GeminiGenerateContentRequest request = GeminiGenerateContentRequest.builder()
                    .contents(List.of(GeminiContent.builder()
                            .role("user")
                            .parts(List.of(GeminiPart.builder().text("Hi").build()))
                            .build()))
                    .build();

            // When
            GeminiGenerateContentResponse actualResponse = subject.generateContent(TEST_MODEL_NAME, request);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
        }

        @Test
        void shouldSendCorrectHttpRequest() {
            // Given
            GeminiGenerateContentResponse expectedResponse = createGenerateContentResponse("Test");
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            GeminiService subject = createService(mockHttpClient);

            GeminiGenerateContentRequest request = GeminiGenerateContentRequest.builder()
                    .contents(List.of(GeminiContent.builder()
                            .role("user")
                            .parts(List.of(GeminiPart.builder().text("Hi").build()))
                            .build()))
                    .build();

            // When
            subject.generateContent(TEST_MODEL_NAME, request);

            // Then
            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.POST);
            assertThat(sentRequest.url()).isEqualTo(TEST_BASE_URL + "/models/" + TEST_MODEL_NAME + ":generateContent");
            assertThat(sentRequest.headers().get("Content-Type")).containsExactly("application/json");
            assertThat(sentRequest.headers().get("User-Agent")).containsExactly("LangChain4j");
            assertThat(sentRequest.headers().get("x-goog-api-key")).containsExactly(TEST_API_KEY);
            assertThat(sentRequest.body()).isEqualTo(Json.toJson(request));
        }

        @Test
        void shouldUseDefaultBaseUrl() {
            // Given
            GeminiGenerateContentResponse expectedResponse = createGenerateContentResponse("Test");
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            GeminiService subject = createService(mockHttpClient, null, null);

            GeminiGenerateContentRequest request = GeminiGenerateContentRequest.builder()
                    .contents(List.of(GeminiContent.builder()
                            .role("user")
                            .parts(List.of(GeminiPart.builder().text("Hi").build()))
                            .build()))
                    .build();

            // When
            subject.generateContent(TEST_MODEL_NAME, request);

            // Then
            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.url()).startsWith("https://generativelanguage.googleapis.com/v1beta");
        }
    }

    @Nested
    class CountTokensTest {

        @Test
        void shouldSendCountTokensRequest() {
            // Given
            GeminiCountTokensResponse expectedResponse = new GeminiCountTokensResponse();
            expectedResponse.setTotalTokens(42);

            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            GeminiService subject = createService(mockHttpClient);

            GeminiCountTokensRequest request = new GeminiCountTokensRequest();
            request.setContents(List.of(GeminiContent.builder()
                    .role("user")
                    .parts(List.of(
                            GeminiPart.builder().text("Count these tokens").build()))
                    .build()));

            // When
            GeminiCountTokensResponse actualResponse = subject.countTokens(TEST_MODEL_NAME, request);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
        }

        @Test
        void shouldSendCorrectCountTokensHttpRequest() {
            // Given
            GeminiCountTokensResponse expectedResponse = new GeminiCountTokensResponse();
            expectedResponse.setTotalTokens(10);

            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            GeminiService subject = createService(mockHttpClient);

            GeminiCountTokensRequest request = new GeminiCountTokensRequest();
            request.setContents(List.of(GeminiContent.builder()
                    .role("user")
                    .parts(List.of(GeminiPart.builder().text("Test").build()))
                    .build()));

            // When
            subject.countTokens(TEST_MODEL_NAME, request);

            // Then
            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.POST);
            assertThat(sentRequest.url()).isEqualTo(TEST_BASE_URL + "/models/" + TEST_MODEL_NAME + ":countTokens");
            assertThat(sentRequest.headers().get("x-goog-api-key")).containsExactly(TEST_API_KEY);
        }
    }

    @Nested
    class EmbedTest {

        @Test
        void shouldSendEmbedRequest() {
            // Given
            var embedding = new GoogleAiEmbeddingResponseValues();
            embedding.setValues(List.of(0.1f, 0.2f, 0.3f));

            GoogleAiEmbeddingResponse expectedResponse = new GoogleAiEmbeddingResponse();
            expectedResponse.setEmbedding(embedding);

            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            GeminiService subject = createService(mockHttpClient);

            GoogleAiEmbeddingRequest request = createEmptyEmbeddingRequest();
            request.setContent(GeminiContent.builder()
                    .parts(List.of(GeminiPart.builder().text("Embed this").build()))
                    .build());

            // When
            GoogleAiEmbeddingResponse actualResponse = subject.embed(TEST_MODEL_NAME, request);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
        }

        @Test
        void shouldSendCorrectEmbedHttpRequest() {
            // Given
            var embedding = new GoogleAiEmbeddingResponseValues();
            embedding.setValues(List.of(0.1f, 0.2f));

            GoogleAiEmbeddingResponse expectedResponse = new GoogleAiEmbeddingResponse();
            expectedResponse.setEmbedding(embedding);

            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            GeminiService subject = createService(mockHttpClient);

            GoogleAiEmbeddingRequest request = createEmptyEmbeddingRequest();
            request.setContent(GeminiContent.builder()
                    .parts(List.of(GeminiPart.builder().text("Test").build()))
                    .build());

            // When
            subject.embed(TEST_MODEL_NAME, request);

            // Then
            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.POST);
            assertThat(sentRequest.url()).isEqualTo(TEST_BASE_URL + "/models/" + TEST_MODEL_NAME + ":embedContent");
            assertThat(sentRequest.headers().get("x-goog-api-key")).containsExactly(TEST_API_KEY);
        }
    }

    @Nested
    class BatchEmbedTest {

        @Test
        void shouldSendBatchEmbedRequest() {
            // Given
            var embedding1 = new GoogleAiEmbeddingResponseValues();
            embedding1.setValues(List.of(0.1f, 0.2f));
            var embedding2 = new GoogleAiEmbeddingResponseValues();
            embedding2.setValues(List.of(0.3f, 0.4f));

            GoogleAiBatchEmbeddingResponse expectedResponse = new GoogleAiBatchEmbeddingResponse();
            expectedResponse.setEmbeddings(List.of(embedding1, embedding2));

            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            GeminiService subject = createService(mockHttpClient);

            GoogleAiEmbeddingRequest request1 = createEmptyEmbeddingRequest();
            request1.setContent(GeminiContent.builder()
                    .parts(List.of(GeminiPart.builder().text("First").build()))
                    .build());

            GoogleAiEmbeddingRequest request2 = createEmptyEmbeddingRequest();
            request2.setContent(GeminiContent.builder()
                    .parts(List.of(GeminiPart.builder().text("Second").build()))
                    .build());

            GoogleAiBatchEmbeddingRequest batchRequest = new GoogleAiBatchEmbeddingRequest();
            batchRequest.setRequests(List.of(request1, request2));

            // When
            GoogleAiBatchEmbeddingResponse actualResponse = subject.batchEmbed(TEST_MODEL_NAME, batchRequest);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
        }

        @Test
        void shouldSendCorrectBatchEmbedHttpRequest() {
            // Given
            GoogleAiBatchEmbeddingResponse expectedResponse = new GoogleAiBatchEmbeddingResponse();
            expectedResponse.setEmbeddings(List.of());

            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            GeminiService subject = createService(mockHttpClient);

            GoogleAiBatchEmbeddingRequest request = new GoogleAiBatchEmbeddingRequest();
            request.setRequests(List.of());

            // When
            subject.batchEmbed(TEST_MODEL_NAME, request);

            // Then
            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.POST);
            assertThat(sentRequest.url())
                    .isEqualTo(TEST_BASE_URL + "/models/" + TEST_MODEL_NAME + ":batchEmbedContents");
            assertThat(sentRequest.headers().get("x-goog-api-key")).containsExactly(TEST_API_KEY);
        }
    }

    @Nested
    class StreamingTest {

        @Test
        void shouldStreamGenerateContentResponse() throws Exception {
            // Given
            List<ServerSentEvent> events = List.of(
                    new ServerSentEvent("event1", Json.toJson(createGenerateContentResponse("Hello"))),
                    new ServerSentEvent("event2", Json.toJson(createGenerateContentResponse(" world"))),
                    new ServerSentEvent("event3", Json.toJson(createGenerateContentResponse("!"))));
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(events);

            GeminiService subject = createService(mockHttpClient);

            GeminiGenerateContentRequest request = GeminiGenerateContentRequest.builder()
                    .contents(List.of(GeminiContent.builder()
                            .role("user")
                            .parts(List.of(GeminiPart.builder().text("Hi").build()))
                            .build()))
                    .build();

            CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
            StringBuilder partialResponses = new StringBuilder();

            // When
            subject.generateContentStream(TEST_MODEL_NAME, request, false, null, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    partialResponses.append(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    futureResponse.complete(completeResponse);
                }

                @Override
                public void onError(Throwable error) {
                    futureResponse.completeExceptionally(error);
                }
            });

            ChatResponse response = futureResponse.get(5, TimeUnit.SECONDS);

            // Then
            assertThat(response).isNotNull();
            assertThat(partialResponses.toString()).contains("Hello", "world", "!");
        }

        @Test
        void shouldSendCorrectStreamingHttpRequest() throws Exception {
            // Given
            List<ServerSentEvent> events =
                    List.of(new ServerSentEvent("event1", Json.toJson(createGenerateContentResponse("Test"))));
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(events);

            GeminiService subject = createService(mockHttpClient);

            GeminiGenerateContentRequest request = GeminiGenerateContentRequest.builder()
                    .contents(List.of(GeminiContent.builder()
                            .role("user")
                            .parts(List.of(GeminiPart.builder().text("Hi").build()))
                            .build()))
                    .build();

            CompletableFuture<Void> futureComplete = new CompletableFuture<>();

            // When
            subject.generateContentStream(TEST_MODEL_NAME, request, false, null, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {}

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    futureComplete.complete(null);
                }

                @Override
                public void onError(Throwable error) {
                    futureComplete.completeExceptionally(error);
                }
            });

            futureComplete.get(5, TimeUnit.SECONDS);

            // Then
            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.POST);
            assertThat(sentRequest.url())
                    .isEqualTo(TEST_BASE_URL + "/models/" + TEST_MODEL_NAME + ":streamGenerateContent?alt=sse");
            assertThat(sentRequest.headers().get("x-goog-api-key")).containsExactly(TEST_API_KEY);
            assertThat(sentRequest.body()).isEqualTo(Json.toJson(request));
        }
    }

    @Nested
    class TimeoutTest {

        @Test
        void shouldUseCustomTimeout() {
            // Given
            GeminiGenerateContentResponse expectedResponse = createGenerateContentResponse("Test");
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            Duration customTimeout = Duration.ofSeconds(30);
            GeminiService subject = createService(mockHttpClient, customTimeout);

            GeminiGenerateContentRequest request = GeminiGenerateContentRequest.builder()
                    .contents(List.of(GeminiContent.builder()
                            .role("user")
                            .parts(List.of(GeminiPart.builder().text("Hi").build()))
                            .build()))
                    .build();

            // When
            GeminiGenerateContentResponse actualResponse = subject.generateContent(TEST_MODEL_NAME, request);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
        }
    }

    private static GeminiService createService(MockHttpClient mockHttpClient) {
        return createService(mockHttpClient, null);
    }

    private static GeminiService createService(MockHttpClient mockHttpClient, Duration timeout) {
        return createService(mockHttpClient, TEST_BASE_URL, timeout);
    }

    private static GeminiService createService(MockHttpClient mockHttpClient, String baseUrl, Duration timeout) {
        return new GeminiService(
                new MockHttpClientBuilder(mockHttpClient), TEST_API_KEY, baseUrl, false, false, false, null, timeout);
    }

    private static GeminiGenerateContentResponse createGenerateContentResponse(String text) {
        var candidate = GeminiCandidate.builder()
                .content(GeminiContent.builder()
                        .role("model")
                        .parts(List.of(GeminiPart.builder().text(text).build()))
                        .build())
                .build();
        return new GeminiGenerateContentResponse("responseId", "modelName", List.of(candidate), null, null);
    }

    private static GoogleAiEmbeddingRequest createEmptyEmbeddingRequest() {
        return new GoogleAiEmbeddingRequest("modelName", null, null, null, 756);
    }
}
