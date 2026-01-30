package dev.langchain4j.model.googleai;

import static dev.langchain4j.model.googleai.GeminiService.BatchOperationType.BATCH_GENERATE_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateRequest;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiBatchEmbeddingRequest;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiBatchEmbeddingResponse;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingRequest;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingResponse;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingResponse.GeminiEmbeddingResponseValues;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate;
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
                    .contents(List.of(new GeminiContent(
                            List.of(GeminiContent.GeminiPart.builder()
                                    .text("Hi")
                                    .build()),
                            "user")))
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
                    .contents(List.of(new GeminiContent(
                            List.of(GeminiContent.GeminiPart.builder()
                                    .text("Hi")
                                    .build()),
                            "user")))
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
                    .contents(List.of(new GeminiContent(
                            List.of(GeminiContent.GeminiPart.builder()
                                    .text("Hi")
                                    .build()),
                            "user")))
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
            var expectedResponse = new GeminiCountTokensResponse(42);

            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            GeminiService subject = createService(mockHttpClient);

            GeminiCountTokensRequest request = new GeminiCountTokensRequest(
                    List.of(new GeminiContent(
                            List.of(GeminiContent.GeminiPart.builder()
                                    .text("Count these tokens")
                                    .build()),
                            "user")),
                    null);

            // When
            var actualResponse = subject.countTokens(TEST_MODEL_NAME, request);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
        }

        @Test
        void shouldSendCorrectCountTokensHttpRequest() {
            // Given
            var expectedResponse = new GeminiCountTokensResponse(42);

            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            GeminiService subject = createService(mockHttpClient);

            GeminiCountTokensRequest request = new GeminiCountTokensRequest(
                    List.of(new GeminiContent(
                            List.of(GeminiContent.GeminiPart.builder()
                                    .text("Test")
                                    .build()),
                            "user")),
                    null);

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
            var embedding = new GeminiEmbeddingResponseValues(List.of(0.1f, 0.2f, 0.3f));
            var expectedResponse = new GeminiEmbeddingResponse(embedding);

            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            GeminiService subject = createService(mockHttpClient);

            var request = createEmbeddingRequest(new GeminiContent(
                    List.of(GeminiContent.GeminiPart.builder()
                            .text("Embed this")
                            .build()),
                    null));

            // When
            GeminiEmbeddingResponse actualResponse = subject.embed(TEST_MODEL_NAME, request);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
        }

        @Test
        void shouldSendCorrectEmbedHttpRequest() {
            // Given
            var embedding = new GeminiEmbeddingResponseValues(List.of(0.1f, 0.2f));
            var expectedResponse = new GeminiEmbeddingResponse(embedding);

            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            GeminiService subject = createService(mockHttpClient);

            var request = createEmbeddingRequest(new GeminiContent(
                    List.of(GeminiContent.GeminiPart.builder().text("Test").build()), null));

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
            var embedding1 = new GeminiEmbeddingResponseValues(List.of(0.1f, 0.2f));
            var embedding2 = new GeminiEmbeddingResponseValues(List.of(0.3f, 0.4f));
            var expectedResponse = new GeminiBatchEmbeddingResponse(List.of(embedding1, embedding2));

            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            GeminiService subject = createService(mockHttpClient);

            var request1 = createEmbeddingRequest(new GeminiContent(
                    List.of(GeminiContent.GeminiPart.builder().text("First").build()), null));

            var request2 = createEmbeddingRequest(new GeminiContent(
                    List.of(GeminiContent.GeminiPart.builder().text("Second").build()), null));

            var batchRequest = new GeminiBatchEmbeddingRequest(List.of(request1, request2));

            // When
            var actualResponse = subject.batchEmbed(TEST_MODEL_NAME, batchRequest);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
        }

        @Test
        void shouldSendCorrectBatchEmbedHttpRequest() {
            // Given
            var expectedResponse = new GeminiBatchEmbeddingResponse(List.of());

            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            GeminiService subject = createService(mockHttpClient);

            var request = new GeminiBatchEmbeddingRequest(List.of());

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
                    .contents(List.of(new GeminiContent(
                            List.of(GeminiContent.GeminiPart.builder()
                                    .text("Hi")
                                    .build()),
                            "user")))
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
                    .contents(List.of(new GeminiContent(
                            List.of(GeminiContent.GeminiPart.builder()
                                    .text("Hi")
                                    .build()),
                            "user")))
                    .build();

            CompletableFuture<Void> futureComplete = new CompletableFuture<>();

            // When
            subject.generateContentStream(TEST_MODEL_NAME, request, false, null, new StreamingChatResponseHandler() {
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
                    .contents(List.of(new GeminiContent(
                            List.of(GeminiContent.GeminiPart.builder()
                                    .text("Hi")
                                    .build()),
                            "user")))
                    .build();

            // When
            GeminiGenerateContentResponse actualResponse = subject.generateContent(TEST_MODEL_NAME, request);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
        }
    }

    @Nested
    class BatchGenerateContentTest {
        @Test
        void shouldSendBatchGenerateContentRequest() {
            // Given
            BatchRequestResponse.Operation<?> expectedResponse =
                    new BatchRequestResponse.Operation<>("operations/test-123", null, false, null, null);
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);
            GeminiService subject = createService(mockHttpClient);

            GeminiGenerateContentRequest contentRequest = GeminiGenerateContentRequest.builder()
                    .contents(List.of(new GeminiContent(
                            List.of(GeminiPart.builder().text("Test").build()), "user")))
                    .build();

            BatchCreateRequest<?> request = new BatchCreateRequest<>(new BatchCreateRequest.Batch<>(
                    "test-batch",
                    new BatchRequestResponse.BatchCreateRequest.InputConfig<>(
                            new BatchRequestResponse.BatchCreateRequest.Requests<>(
                                    List.of(new BatchRequestResponse.BatchCreateRequest.InlinedRequest<>(
                                            contentRequest, null)))),
                    1L));

            // When
            BatchRequestResponse.Operation<?> actualResponse =
                    subject.batchCreate(TEST_MODEL_NAME, request, BATCH_GENERATE_CONTENT);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
        }

        @Test
        void shouldSendCorrectBatchGenerateContentHttpRequest() {
            // Given
            BatchRequestResponse.Operation<?> expectedResponse =
                    new BatchRequestResponse.Operation<>("operations/test-123", null, false, null, null);
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);
            GeminiService subject = createService(mockHttpClient);

            GeminiGenerateContentRequest contentRequest = GeminiGenerateContentRequest.builder()
                    .contents(List.of(new GeminiContent(
                            List.of(GeminiPart.builder().text("Test").build()), "user")))
                    .build();

            BatchCreateRequest<Object> request = new BatchCreateRequest<>(new BatchCreateRequest.Batch<>(
                    "test-batch",
                    new BatchRequestResponse.BatchCreateRequest.InputConfig<>(
                            new BatchRequestResponse.BatchCreateRequest.Requests<>(
                                    List.of(new BatchRequestResponse.BatchCreateRequest.InlinedRequest<>(
                                            contentRequest, null)))),
                    1L));

            // When
            subject.batchCreate(TEST_MODEL_NAME, request, BATCH_GENERATE_CONTENT);

            // Then
            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.POST);
            assertThat(sentRequest.url())
                    .isEqualTo(TEST_BASE_URL + "/models/" + TEST_MODEL_NAME + ":batchGenerateContent");
            assertThat(sentRequest.headers().get("Content-Type")).containsExactly("application/json");
            assertThat(sentRequest.headers().get("User-Agent")).containsExactly("LangChain4j");
            assertThat(sentRequest.headers().get("x-goog-api-key")).containsExactly(TEST_API_KEY);
            assertThat(sentRequest.body()).isEqualTo(Json.toJson(request));
        }
    }

    @Nested
    class BatchRetrieveBatchTest {
        @Test
        void shouldSendBatchRetrieveBatchRequest() {
            // Given
            BatchRequestResponse.Operation<?> expectedResponse =
                    new BatchRequestResponse.Operation<>("batches/test-batch", null, true, null, null);
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);
            GeminiService subject = createService(mockHttpClient);

            String batchName = "batches/test-batch";

            // When
            BatchRequestResponse.Operation<?> actualResponse = subject.batchRetrieveBatch(batchName);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
        }

        @Test
        void shouldSendCorrectBatchRetrieveBatchHttpRequest() {
            // Given
            BatchRequestResponse.Operation<?> expectedResponse =
                    new BatchRequestResponse.Operation<>("batches/test-batch", null, true, null, null);
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);
            GeminiService subject = createService(mockHttpClient);

            String batchName = "batches/test-batch";

            // When
            subject.batchRetrieveBatch(batchName);

            // Then
            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.GET);
            assertThat(sentRequest.url()).isEqualTo(TEST_BASE_URL + "/" + batchName);
            assertThat(sentRequest.headers().get("x-goog-api-key")).containsExactly(TEST_API_KEY);
        }
    }

    @Nested
    class BatchCancelBatchTest {
        @Test
        void shouldSendBatchCancelBatchRequest() {
            // Given
            SuccessfulHttpResponse httpResponse =
                    SuccessfulHttpResponse.builder().statusCode(200).body("{}").build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);
            GeminiService subject = createService(mockHttpClient);

            String batchName = "batches/test-batch";

            // When
            Void actualResponse = subject.batchCancelBatch(batchName);

            // Then
            assertThat(actualResponse).isNull();
        }

        @Test
        void shouldSendCorrectBatchCancelBatchHttpRequest() {
            // Given
            SuccessfulHttpResponse httpResponse =
                    SuccessfulHttpResponse.builder().statusCode(200).body("{}").build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);
            GeminiService subject = createService(mockHttpClient);

            String batchName = "batches/test-batch";

            // When
            subject.batchCancelBatch(batchName);

            // Then
            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.POST);
            assertThat(sentRequest.url()).isEqualTo(TEST_BASE_URL + "/" + batchName + ":cancel");
            assertThat(sentRequest.headers().get("x-goog-api-key")).containsExactly(TEST_API_KEY);
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
        var candidate = new GeminiCandidate(
                new GeminiContent(
                        List.of(GeminiContent.GeminiPart.builder().text(text).build()), "model"),
                null,
                null);
        return new GeminiGenerateContentResponse("responseId", "modelName", List.of(candidate), null, null);
    }

    private static GeminiEmbeddingRequest createEmbeddingRequest(GeminiContent content) {
        return new GeminiEmbeddingRequest("modelName", content, null, null, 756);
    }
}
