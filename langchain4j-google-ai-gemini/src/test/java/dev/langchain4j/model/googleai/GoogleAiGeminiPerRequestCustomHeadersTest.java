package dev.langchain4j.model.googleai;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.HttpParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class GoogleAiGeminiPerRequestCustomHeadersTest {

    private static final String GENERATE_CONTENT_RESPONSE =
            """
            {
              "candidates": [
                {
                  "content": {
                    "role": "model",
                    "parts": [ { "text": "Hello!" } ]
                  },
                  "finishReason": "STOP"
                }
              ],
              "usageMetadata": {
                "promptTokenCount": 1,
                "candidatesTokenCount": 1,
                "totalTokenCount": 2
              }
            }
            """;

    private static final Map<String, String> MODEL_HEADERS = Map.of("X-Model-Header", "model", "X-Shared", "model");

    private static final ChatRequest CHAT_REQUEST = ChatRequest.builder()
            .messages(UserMessage.from("Hello"))
            .customHeaders(Map.of("x-shared", "request", "X-Correlation-Id", "123"))
            .build();

    private static final ChatRequest TRACKING_REQUEST = ChatRequest.builder()
            .messages(UserMessage.from("Hello"))
            .customHeaders(Map.of(
                    "user-agent", "google-adk/1.2.0 gl-java/21",
                    "x-goog-api-client", "b/2",
                    "x-shared", "request"))
            .build();

    private static final ChatRequestParameters DEFAULT_PARAMETERS_WITH_HEADERS = ChatRequestParameters.builder()
            .httpParameters(HttpParameters.builder()
                    .customHeaders(Map.of("X-Default", "d", "X-Shared", "default"))
                    .build())
            .build();

    private static final ChatRequest REQUEST_WITH_SHARED_HEADER = ChatRequest.builder()
            .messages(UserMessage.from("Hello"))
            .customHeaders(Map.of("x-shared", "request"))
            .build();

    @Test
    void chat_model_should_send_per_request_headers() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(generateContentResponse());

        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gemini-model")
                .customHeaders(MODEL_HEADERS)
                .build();

        // when
        model.chat(CHAT_REQUEST);

        // then
        assertPerRequestHeadersSent(mockHttpClient.request());
    }

    @Test
    void streaming_chat_model_should_send_per_request_headers() throws Exception {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(
                List.of(new ServerSentEvent(null, GENERATE_CONTENT_RESPONSE.replace("\n", ""))));

        GoogleAiGeminiStreamingChatModel model = GoogleAiGeminiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gemini-model")
                .customHeaders(MODEL_HEADERS)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(CHAT_REQUEST, handler(futureResponse));
        futureResponse.get(5, SECONDS);

        // then
        assertPerRequestHeadersSent(mockHttpClient.request());
    }

    @Test
    void per_request_headers_should_append_to_user_agent_and_replace_api_key_header() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(generateContentResponse());

        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("model-key")
                .modelName("gemini-model")
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .customHeaders(Map.of("user-agent", "my-app/1.0", "X-Goog-Api-Key", "request-key"))
                .build();

        // when
        model.chat(chatRequest);

        // then
        Map<String, List<String>> headers = mockHttpClient.request().headers();
        assertThat(headers)
                .containsEntry("user-agent", List.of("LangChain4j my-app/1.0"))
                .containsEntry("x-goog-api-key", List.of("request-key"));
        assertThat(headers.keySet())
                .filteredOn(name -> name.equalsIgnoreCase("user-agent") || name.equalsIgnoreCase("x-goog-api-key"))
                .containsExactlyInAnyOrder("user-agent", "X-Goog-Api-Key");
    }

    @Test
    void chat_model_should_append_per_request_user_agent_and_x_goog_api_client() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(generateContentResponse());

        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gemini-model")
                .customHeaders(Map.of("x-goog-api-client", "a/1", "X-Shared", "model"))
                .build();

        // when
        model.chat(TRACKING_REQUEST);

        // then
        assertTrackingHeadersAppended(mockHttpClient.request());
    }

    @Test
    void streaming_chat_model_should_append_per_request_user_agent_and_x_goog_api_client() throws Exception {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(
                List.of(new ServerSentEvent(null, GENERATE_CONTENT_RESPONSE.replace("\n", ""))));

        GoogleAiGeminiStreamingChatModel model = GoogleAiGeminiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gemini-model")
                .customHeaders(Map.of("x-goog-api-client", "a/1", "X-Shared", "model"))
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(TRACKING_REQUEST, handler(futureResponse));
        futureResponse.get(5, SECONDS);

        // then
        assertTrackingHeadersAppended(mockHttpClient.request());
    }

    @Test
    void per_request_x_goog_api_client_should_be_sent_as_is_when_model_sends_none() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(generateContentResponse());

        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gemini-model")
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .customHeaders(Map.of("x-goog-api-client", "b/2"))
                .build();

        // when
        model.chat(chatRequest);

        // then
        assertThat(mockHttpClient.request().headers()).containsEntry("x-goog-api-client", List.of("b/2"));
    }

    @Test
    void chat_model_should_not_send_per_request_headers_of_previous_request() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(generateContentResponse());

        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gemini-model")
                .build();

        // when
        model.chat(CHAT_REQUEST);
        model.chat("Hello again");

        // then
        assertThat(mockHttpClient.requests()).hasSize(2);
        assertThat(mockHttpClient.requests().get(1).headers()).doesNotContainKey("X-Correlation-Id");
    }

    @Test
    void chat_model_should_send_headers_from_default_request_parameters() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(generateContentResponse());

        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gemini-model")
                .defaultRequestParameters(DEFAULT_PARAMETERS_WITH_HEADERS)
                .build();

        // when
        model.chat(REQUEST_WITH_SHARED_HEADER);

        // then
        assertDefaultAndRequestHeadersSent(mockHttpClient.request());
    }

    @Test
    void streaming_chat_model_should_send_headers_from_default_request_parameters() throws Exception {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(List.of(new ServerSentEvent(
                null, GENERATE_CONTENT_RESPONSE.replace("\n", ""))));

        GoogleAiGeminiStreamingChatModel model = GoogleAiGeminiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gemini-model")
                .defaultRequestParameters(DEFAULT_PARAMETERS_WITH_HEADERS)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(REQUEST_WITH_SHARED_HEADER, handler(futureResponse));
        futureResponse.get(5, SECONDS);

        // then
        assertDefaultAndRequestHeadersSent(mockHttpClient.request());
    }

    private static void assertTrackingHeadersAppended(HttpRequest request) {
        Map<String, List<String>> headers = request.headers();
        assertThat(headers)
                .containsEntry("user-agent", List.of("LangChain4j google-adk/1.2.0 gl-java/21"))
                .containsEntry("x-goog-api-client", List.of("a/1 b/2"))
                .containsEntry("x-shared", List.of("request"));
        assertThat(headers.keySet())
                .filteredOn(name -> name.equalsIgnoreCase("x-shared"))
                .containsExactly("x-shared");
    }

    private static void assertDefaultAndRequestHeadersSent(HttpRequest request) {
        Map<String, List<String>> headers = request.headers();
        assertThat(headers).containsEntry("X-Default", List.of("d")).containsEntry("x-shared", List.of("request"));
        assertThat(headers.keySet())
                .filteredOn(name -> name.equalsIgnoreCase("x-shared"))
                .containsExactly("x-shared");
    }

    private static void assertPerRequestHeadersSent(HttpRequest request) {
        Map<String, List<String>> headers = request.headers();
        assertThat(headers)
                .containsEntry("X-Model-Header", List.of("model"))
                .containsEntry("x-shared", List.of("request"))
                .containsEntry("X-Correlation-Id", List.of("123"))
                .containsEntry("x-goog-api-key", List.of("test-key"));
        assertThat(headers.keySet())
                .filteredOn(name -> name.equalsIgnoreCase("x-shared"))
                .containsExactly("x-shared");
    }

    private static SuccessfulHttpResponse generateContentResponse() {
        return SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(GENERATE_CONTENT_RESPONSE)
                .build();
    }

    private static StreamingChatResponseHandler handler(CompletableFuture<ChatResponse> futureResponse) {
        return new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        };
    }
}
