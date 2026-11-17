package dev.langchain4j.model.mistralai;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionRequest;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.HttpParameters;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionResponse;
import dev.langchain4j.model.mistralai.internal.client.ParsedAndRawResponse;
import java.util.Arrays;

class MistralAiPerRequestCustomHeadersTest {

    private static final String MODEL = "mistral-small-latest";

    private static final String CHAT_COMPLETION_RESPONSE =
            """
            {
                "id": "chatcmpl-1",
                "object": "chat.completion",
                "created": 1778075620,
                "model": "mistral-small-latest",
                "choices": [{
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": "Hello"
                    },
                    "finish_reason": "stop"
                }],
                "usage": {"prompt_tokens": 5, "completion_tokens": 1, "total_tokens": 6}
            }
            """;

    private static final List<ServerSentEvent> STREAMING_EVENTS = List.of(
            new ServerSentEvent(
                    null,
                    "{\"id\":\"abc123\",\"model\":\"" + MODEL
                            + "\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"}}]}"),
            new ServerSentEvent(
                    null,
                    "{\"id\":\"abc123\",\"model\":\"" + MODEL
                            + "\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}],"
                            + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":1,\"total_tokens\":6}}"),
            new ServerSentEvent(null, "[DONE]"));

    private static final Map<String, String> MODEL_HEADERS = Map.of("X-Model-Header", "model", "X-Shared", "model");

    private static final ChatRequest CHAT_REQUEST = ChatRequest.builder()
            .messages(UserMessage.from("Hello"))
            .customHeaders(Map.of("x-shared", "request", "X-Correlation-Id", "123"))
            .build();

    @Test
    void chat_model_should_send_per_request_headers() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(chatCompletionResponse());

        MistralAiChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName(MODEL)
                .customHeaders(MODEL_HEADERS)
                .build();

        // when
        model.chat(CHAT_REQUEST);

        // then
        assertPerRequestHeadersSent(mockHttpClient.request());
    }

    @Test
    void per_request_header_should_replace_authorization_header() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(chatCompletionResponse());

        MistralAiChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("model-key")
                .modelName(MODEL)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .customHeaders(Map.of("authorization", "Bearer request-key"))
                .build();

        // when
        model.chat(chatRequest);

        // then
        Map<String, List<String>> headers = mockHttpClient.request().headers();
        assertThat(headers).containsEntry("authorization", List.of("Bearer request-key"));
        assertThat(headers.keySet())
                .filteredOn(name -> name.equalsIgnoreCase("authorization"))
                .containsExactly("authorization");
    }

    @Test
    void chat_model_should_not_send_per_request_headers_of_previous_request() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(chatCompletionResponse());

        MistralAiChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName(MODEL)
                .build();

        // when
        model.chat(CHAT_REQUEST);
        model.chat("Hello again");

        // then
        assertThat(mockHttpClient.requests()).hasSize(2);
        assertThat(mockHttpClient.requests().get(1).headers()).doesNotContainKey("X-Correlation-Id");
    }

    @Test
    void streaming_chat_model_should_send_per_request_headers() throws Exception {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(STREAMING_EVENTS);

        MistralAiStreamingChatModel model = MistralAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName(MODEL)
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
    void batch_chat_model_should_reject_per_request_headers() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(chatCompletionResponse());

        MistralAiBatchChatModel model = MistralAiBatchChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName(MODEL)
                .build();

        // when-then
        assertThatThrownBy(() -> model.submit(new BatchRequest<>(List.of(CHAT_REQUEST))))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("httpParameters")
                .hasMessageContaining("batch");
        assertThat(mockHttpClient.requests()).isEmpty();
    }

    @Test
    void client_without_per_request_header_support_should_fail_only_when_headers_are_given() {

        // given
        MistralAiClient client = mock(MistralAiClient.class, CALLS_REAL_METHODS);
        MistralAiChatCompletionRequest request =
                MistralAiChatCompletionRequest.builder().build();
        HttpParameters headers =
                HttpParameters.builder().customHeaders(Map.of("X-A", "1")).build();

        // when-then
        assertThatThrownBy(() -> client.chatCompletionWithRawResponse(request, headers))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("per-request custom HTTP headers");

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
        client.streamingChatCompletion(request, handler(futureResponse), false, headers);
        assertThat(futureResponse)
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isExactlyInstanceOf(UnsupportedFeatureException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void client_spi_defaults_should_delegate_to_old_methods_without_headers() {

        // given
        MistralAiClient client = mock(MistralAiClient.class, CALLS_REAL_METHODS);
        MistralAiChatCompletionRequest request =
                MistralAiChatCompletionRequest.builder().build();
        StreamingChatResponseHandler handler = handler(new CompletableFuture<>());
        ParsedAndRawResponse<MistralAiChatCompletionResponse> response = mock(ParsedAndRawResponse.class);
        doReturn(response).when(client).chatCompletionWithRawResponse(request);
        doNothing().when(client).streamingChatCompletion(request, handler, true);

        for (HttpParameters noHeaders : Arrays.asList(
                null,
                HttpParameters.builder().build(),
                HttpParameters.builder().customHeaders(Map.of()).build())) {

            // when-then
            assertThat(client.chatCompletionWithRawResponse(request, noHeaders)).isSameAs(response);
            client.streamingChatCompletion(request, handler, true, noHeaders);
        }

        verify(client, times(3)).chatCompletionWithRawResponse(request);
        verify(client, times(3)).streamingChatCompletion(request, handler, true);
    }

    @Test
    void chat_model_should_merge_headers_of_default_request_parameters_with_per_request_headers() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(chatCompletionResponse());

        MistralAiChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName(MODEL)
                .defaultRequestParameters(DEFAULT_PARAMETERS_WITH_HEADERS)
                .build();

        // when
        model.chat(REQUEST_OVERRIDING_DEFAULT_HEADER);

        // then
        assertDefaultAndPerRequestHeadersMerged(mockHttpClient.request());
    }

    @Test
    void streaming_chat_model_should_merge_headers_of_default_request_parameters_with_per_request_headers()
            throws Exception {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(STREAMING_EVENTS);

        MistralAiStreamingChatModel model = MistralAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName(MODEL)
                .defaultRequestParameters(DEFAULT_PARAMETERS_WITH_HEADERS)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(REQUEST_OVERRIDING_DEFAULT_HEADER, handler(futureResponse));
        futureResponse.get(5, SECONDS);

        // then
        assertDefaultAndPerRequestHeadersMerged(mockHttpClient.request());
    }

    @Test
    void batch_chat_model_should_reject_headers_in_default_request_parameters_at_build_time() {

        assertThatThrownBy(() -> MistralAiBatchChatModel.builder()
                        .httpClientBuilder(new MockHttpClientBuilder(
                                MockHttpClient.thatAlwaysResponds(chatCompletionResponse())))
                        .apiKey("test-key")
                        .modelName(MODEL)
                        .defaultRequestParameters(DEFAULT_PARAMETERS_WITH_HEADERS)
                        .build())
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("batch");
    }

    private static final ChatRequestParameters DEFAULT_PARAMETERS_WITH_HEADERS = ChatRequestParameters.builder()
            .httpParameters(HttpParameters.builder()
                    .customHeaders(Map.of("X-Default", "d", "X-Shared", "default"))
                    .build())
            .build();

    private static final ChatRequest REQUEST_OVERRIDING_DEFAULT_HEADER = ChatRequest.builder()
            .messages(UserMessage.from("Hello"))
            .customHeaders(Map.of("x-shared", "request"))
            .build();

    private static void assertDefaultAndPerRequestHeadersMerged(HttpRequest request) {
        Map<String, List<String>> headers = request.headers();
        assertThat(headers)
                .containsEntry("X-Default", List.of("d"))
                .containsEntry("x-shared", List.of("request"));
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
                .containsEntry("Authorization", List.of("Bearer test-key"));
        assertThat(headers.keySet())
                .filteredOn(name -> name.equalsIgnoreCase("x-shared"))
                .containsExactly("x-shared");
    }

    private static SuccessfulHttpResponse chatCompletionResponse() {
        return SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(CHAT_COMPLETION_RESPONSE)
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

    private static final ChatRequest USER_AGENT_REQUEST = ChatRequest.builder()
            .messages(UserMessage.from("Hello"))
            .customHeaders(Map.of("user-agent", "my-app/1.0", "X-Correlation-Id", "123"))
            .build();

    @Test
    void chat_model_should_append_per_request_user_agent_to_its_own() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(chatCompletionResponse());

        MistralAiChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName(MODEL)
                .build();

        // when
        model.chat(USER_AGENT_REQUEST);

        // then
        assertThat(mockHttpClient.request().headers())
                .containsEntry("User-Agent", List.of("langchain4j-mistral-ai my-app/1.0"))
                .containsEntry("X-Correlation-Id", List.of("123"));
    }

    @Test
    void streaming_chat_model_should_append_per_request_user_agent_to_its_own() throws Exception {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(STREAMING_EVENTS);

        MistralAiStreamingChatModel model = MistralAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName(MODEL)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(USER_AGENT_REQUEST, handler(futureResponse));
        futureResponse.get(5, SECONDS);

        // then
        assertThat(mockHttpClient.request().headers())
                .containsEntry("User-Agent", List.of("langchain4j-mistral-ai my-app/1.0"))
                .containsEntry("X-Correlation-Id", List.of("123"));
    }

    @Test
    void chat_model_should_append_per_request_user_agent_to_the_one_set_on_the_builder() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(chatCompletionResponse());

        MistralAiChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName(MODEL)
                .customHeaders(Map.of("User-Agent", "gateway-client/2.0"))
                .build();

        // when
        model.chat(USER_AGENT_REQUEST);

        // then
        assertThat(mockHttpClient.request().headers()).containsEntry("User-Agent", List.of("gateway-client/2.0 my-app/1.0"));
    }
}

