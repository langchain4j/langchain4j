package dev.langchain4j.model.openai;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.internal.ChatCompletionOptions;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.HttpParameters;
import dev.langchain4j.model.openai.internal.SyncOrAsyncOrStreaming;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import java.util.Arrays;
import org.mockito.MockedStatic;

class OpenAiPerRequestCustomHeadersTest {

    private static final String CHAT_COMPLETION_RESPONSE =
            """
            {
              "id": "chatcmpl-test",
              "object": "chat.completion",
              "created": 1721596428,
              "model": "gpt-4o-mini",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "Hello!"
                  },
                  "finish_reason": "stop"
                }
              ]
            }
            """;

    private static final String RESPONSES_RESPONSE =
            """
            {
              "id": "resp_1",
              "model": "gpt-4o-mini",
              "status": "completed",
              "output": [
                {
                  "type": "message",
                  "role": "assistant",
                  "content": [
                    {
                      "type": "output_text",
                      "text": "Hi"
                    }
                  ]
                }
              ]
            }
            """;

    private static final Map<String, String> MODEL_HEADERS = Map.of("X-Model-Header", "model", "X-Shared", "model");

    private static final ChatRequest CHAT_REQUEST = ChatRequest.builder()
            .messages(UserMessage.from("Hello"))
            .customHeaders(Map.of("x-shared", "request", "X-Correlation-Id", "123"))
            .build();

    @Test
    void chat_model_should_send_per_request_headers() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(chatCompletionResponse());

        OpenAiChatModel model = OpenAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .customHeaders(MODEL_HEADERS)
                .build();

        // when
        model.chat(CHAT_REQUEST);

        // then
        assertPerRequestHeadersSent(mockHttpClient.request());
    }

    @Test
    void chat_model_should_send_per_request_headers_async() throws Exception {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(chatCompletionResponse());

        OpenAiChatModel model = OpenAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .customHeaders(MODEL_HEADERS)
                .build();

        // when
        model.chatAsync(CHAT_REQUEST).get(5, SECONDS);

        // then
        assertPerRequestHeadersSent(mockHttpClient.request());
    }

    @Test
    void per_request_header_should_replace_authorization_header() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(chatCompletionResponse());

        OpenAiChatModel model = OpenAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("model-key")
                .modelName("gpt-4o-mini")
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

        OpenAiChatModel model = OpenAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
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
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(List.of(
                new ServerSentEvent(null, "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"}}]}"),
                new ServerSentEvent(null, "[DONE]")));

        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
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
    void streaming_chat_model_publisher_should_send_per_request_headers() throws Exception {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.withScriptedStreaming();

        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .customHeaders(MODEL_HEADERS)
                .build();

        // when
        model.chat(CHAT_REQUEST).subscribe(new Flow.Subscriber<ChatModelStreamingEvent>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ChatModelStreamingEvent event) {}

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });

        // then
        assertPerRequestHeadersSent(mockHttpClient.nextExchange().request());
    }

    @Test
    void responses_chat_model_should_send_per_request_headers() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("Content-Type", List.of("application/json")))
                .body(RESPONSES_RESPONSE)
                .build());

        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .customHeaders(MODEL_HEADERS)
                .build();

        // when
        model.chat(CHAT_REQUEST);

        // then
        assertPerRequestHeadersSent(mockHttpClient.request());
    }

    @Test
    void responses_streaming_chat_model_should_send_per_request_headers() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(List.of(new ServerSentEvent(
                "response.completed",
                "{\"type\":\"response.completed\",\"response\":" + RESPONSES_RESPONSE.replace("\n", "") + "}")));

        OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .customHeaders(MODEL_HEADERS)
                .build();

        // when
        model.chat(CHAT_REQUEST, handler(new CompletableFuture<>()));

        // then
        assertPerRequestHeadersSent(mockHttpClient.request());
    }

    @Test
    void client_spi_defaults_should_fail_only_when_headers_are_given() {

        // given
        OpenAiClient client = mock(OpenAiClient.class, CALLS_REAL_METHODS);
        ChatCompletionRequest request = ChatCompletionRequest.builder().build();
        ChatCompletionOptions options = new ChatCompletionOptions(false, false);
        HttpParameters headers =
                HttpParameters.builder().customHeaders(Map.of("X-A", "1")).build();

        // when-then
        assertThatThrownBy(() -> client.chatCompletion(request, headers))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("per-request custom HTTP headers");
        assertThat(firstError(client.chatCompletionPublisher(request, options, headers)))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .withMessageContaining("per-request custom HTTP headers");
    }

    @Test
    @SuppressWarnings("unchecked")
    void client_spi_defaults_should_delegate_to_old_methods_without_headers() {

        // given
        OpenAiClient client = mock(OpenAiClient.class, CALLS_REAL_METHODS);
        ChatCompletionRequest request = ChatCompletionRequest.builder().build();
        ChatCompletionOptions options = new ChatCompletionOptions(false, false);
        SyncOrAsyncOrStreaming<ChatCompletionResponse> chatCompletion = mock(SyncOrAsyncOrStreaming.class);
        Flow.Publisher<ChatModelStreamingEvent> publisher = subscriber -> {};
        doReturn(chatCompletion).when(client).chatCompletion(request);
        doReturn(publisher).when(client).chatCompletionPublisher(request, options);

        for (HttpParameters noHeaders : Arrays.asList(
                null,
                HttpParameters.builder().build(),
                HttpParameters.builder().customHeaders(Map.of()).build())) {

            // when-then
            assertThat(client.chatCompletion(request, noHeaders)).isSameAs(chatCompletion);
            assertThat(client.chatCompletionPublisher(request, options, noHeaders)).isSameAs(publisher);
        }

        verify(client, times(3)).chatCompletion(request);
        verify(client, times(3)).chatCompletionPublisher(request, options);
    }

    @Test
    @SuppressWarnings("unchecked")
    void streaming_chat_model_should_report_unsupported_per_request_headers_through_on_error() {

        // given
        OpenAiClient client = mock(OpenAiClient.class, CALLS_REAL_METHODS);
        OpenAiClient.Builder<OpenAiClient, ?> clientBuilder = mock(OpenAiClient.Builder.class, RETURNS_SELF);
        when(clientBuilder.build()).thenReturn(client);

        try (MockedStatic<OpenAiClient> openAiClient = mockStatic(OpenAiClient.class)) {
            openAiClient.when(OpenAiClient::builder).thenReturn(clientBuilder);

            OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                    .apiKey("test-key")
                    .modelName("gpt-4o-mini")
                    .build();

            CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

            // when
            model.chat(CHAT_REQUEST, handler(futureResponse));

            // then
            assertThat(futureResponse)
                    .failsWithin(5, SECONDS)
                    .withThrowableThat()
                    .havingCause()
                    .isExactlyInstanceOf(UnsupportedFeatureException.class);
        }
    }

    @Test
    void chat_model_should_merge_headers_of_default_request_parameters_with_per_request_headers() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(chatCompletionResponse());

        OpenAiChatModel model = OpenAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
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
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(List.of(
                new ServerSentEvent(null, "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"}}]}"),
                new ServerSentEvent(null, "[DONE]")));

        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
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
    void responses_chat_model_should_merge_headers_of_default_request_parameters_with_per_request_headers() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(responsesResponse());

        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .defaultRequestParameters(DEFAULT_PARAMETERS_WITH_HEADERS)
                .build();

        // when
        model.chat(REQUEST_OVERRIDING_DEFAULT_HEADER);

        // then
        assertDefaultAndPerRequestHeadersMerged(mockHttpClient.request());
    }

    @Test
    void responses_chat_model_should_send_per_request_headers_async() throws Exception {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(responsesResponse());

        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .customHeaders(MODEL_HEADERS)
                .build();

        // when
        model.chatAsync(CHAT_REQUEST).get(5, SECONDS);

        // then
        assertPerRequestHeadersSent(mockHttpClient.request());
    }

    @Test
    void responses_streaming_chat_model_publisher_should_send_per_request_headers() throws Exception {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.withScriptedStreaming();

        OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .customHeaders(MODEL_HEADERS)
                .build();

        // when
        firstError(model.chat(CHAT_REQUEST));

        // then
        assertPerRequestHeadersSent(mockHttpClient.nextExchange().request());
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

    private static SuccessfulHttpResponse responsesResponse() {
        return SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("Content-Type", List.of("application/json")))
                .body(RESPONSES_RESPONSE)
                .build();
    }

    private static <T> CompletableFuture<Throwable> firstError(Flow.Publisher<T> publisher) {
        CompletableFuture<Throwable> error = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T item) {}

            @Override
            public void onError(Throwable throwable) {
                error.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                error.complete(null);
            }
        });
        return error;
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

        OpenAiChatModel model = OpenAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .build();

        // when
        model.chat(USER_AGENT_REQUEST);

        // then
        assertThat(mockHttpClient.request().headers())
                .containsEntry("User-Agent", List.of("langchain4j-openai my-app/1.0"))
                .containsEntry("X-Correlation-Id", List.of("123"));
    }

    @Test
    void streaming_chat_model_should_append_per_request_user_agent_to_its_own() throws Exception {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(List.of(
                new ServerSentEvent(null, "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"}}]}"),
                new ServerSentEvent(null, "[DONE]")));

        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(USER_AGENT_REQUEST, handler(futureResponse));
        futureResponse.get(5, SECONDS);

        // then
        assertThat(mockHttpClient.request().headers())
                .containsEntry("User-Agent", List.of("langchain4j-openai my-app/1.0"))
                .containsEntry("X-Correlation-Id", List.of("123"));
    }

    @Test
    void chat_model_should_append_per_request_user_agent_to_the_one_set_on_the_builder() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(chatCompletionResponse());

        OpenAiChatModel model = OpenAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .customHeaders(Map.of("User-Agent", "gateway-client/2.0"))
                .build();

        // when
        model.chat(USER_AGENT_REQUEST);

        // then
        assertThat(mockHttpClient.request().headers()).containsEntry("User-Agent", List.of("gateway-client/2.0 my-app/1.0"));
    }

    @Test
    void responses_chat_model_should_send_per_request_user_agent_as_is_when_it_sends_none() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("Content-Type", List.of("application/json")))
                .body(RESPONSES_RESPONSE)
                .build());

        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .build();

        // when
        model.chat(USER_AGENT_REQUEST);

        // then
        assertThat(mockHttpClient.request().headers()).containsEntry("User-Agent", List.of("my-app/1.0"));
    }

    @Test
    void responses_chat_model_should_append_per_request_user_agent_to_the_one_set_on_the_builder() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("Content-Type", List.of("application/json")))
                .body(RESPONSES_RESPONSE)
                .build());

        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .customHeaders(Map.of("User-Agent", "gateway-client/2.0"))
                .build();

        // when
        model.chat(USER_AGENT_REQUEST);

        // then
        assertThat(mockHttpClient.request().headers()).containsEntry("User-Agent", List.of("gateway-client/2.0 my-app/1.0"));
    }
}

