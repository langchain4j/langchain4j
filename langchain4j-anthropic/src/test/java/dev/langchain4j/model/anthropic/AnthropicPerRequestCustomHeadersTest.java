package dev.langchain4j.model.anthropic;

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
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.client.AnthropicClient;
import dev.langchain4j.model.anthropic.internal.client.AnthropicCreateMessageOptions;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import dev.langchain4j.model.anthropic.internal.client.DefaultAnthropicClient;
import dev.langchain4j.model.anthropic.internal.client.ParsedAndRawResponse;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.HttpParameters;
import java.util.Arrays;
import java.util.HashMap;

class AnthropicPerRequestCustomHeadersTest {

    // language=json
    private static final String MESSAGE_RESPONSE =
            """
            {
              "id": "msg_123",
              "type": "message",
              "role": "assistant",
              "content": [{"type": "text", "text": "Hello"}],
              "model": "claude-haiku-4-5-20251001",
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 5, "output_tokens": 3}
            }
            """;

    private static final List<ServerSentEvent> STREAMING_EVENTS = List.of(
            new ServerSentEvent(
                    "message_start",
                    "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[],\"model\":\"claude-haiku-4-5-20251001\",\"stop_reason\":null,\"usage\":{\"input_tokens\":5,\"output_tokens\":0}}}"),
            new ServerSentEvent(
                    "content_block_start",
                    "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}"),
            new ServerSentEvent(
                    "content_block_delta",
                    "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}"),
            new ServerSentEvent("content_block_stop", "{\"type\":\"content_block_stop\",\"index\":0}"),
            new ServerSentEvent(
                    "message_delta",
                    "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":2}}"),
            new ServerSentEvent("message_stop", "{\"type\":\"message_stop\"}"));

    private static final Map<String, String> MODEL_HEADERS = Map.of("X-Model-Header", "model", "X-Shared", "model");

    private static final ChatRequest CHAT_REQUEST = ChatRequest.builder()
            .messages(UserMessage.from("Hello"))
            .customHeaders(Map.of("x-shared", "request", "X-Correlation-Id", "123"))
            .build();

    @Test
    void chat_model_should_send_per_request_headers() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(messageResponse());

        AnthropicChatModel model = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("claude-haiku-4-5-20251001")
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
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(messageResponse());

        AnthropicChatModel model = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("claude-haiku-4-5-20251001")
                .customHeaders(MODEL_HEADERS)
                .build();

        // when
        model.chatAsync(CHAT_REQUEST).get(5, SECONDS);

        // then
        assertPerRequestHeadersSent(mockHttpClient.request());
    }

    @Test
    void per_request_header_should_replace_api_key_header() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(messageResponse());

        AnthropicChatModel model = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("model-key")
                .modelName("claude-haiku-4-5-20251001")
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .customHeaders(Map.of("X-API-KEY", "request-key"))
                .build();

        // when
        model.chat(chatRequest);

        // then
        Map<String, List<String>> headers = mockHttpClient.request().headers();
        assertThat(headers).containsEntry("x-api-key", List.of("request-key"));
        assertThat(headers.keySet())
                .filteredOn(name -> name.equalsIgnoreCase("x-api-key"))
                .containsExactly("X-API-KEY");
    }

    @Test
    void chat_model_should_not_send_per_request_headers_of_previous_request() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(messageResponse());

        AnthropicChatModel model = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("claude-haiku-4-5-20251001")
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

        AnthropicStreamingChatModel model = AnthropicStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("claude-haiku-4-5-20251001")
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

        AnthropicStreamingChatModel model = AnthropicStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("claude-haiku-4-5-20251001")
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
    void batch_chat_model_should_reject_per_request_headers() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(messageResponse());

        AnthropicBatchChatModel model = AnthropicBatchChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("claude-haiku-4-5-20251001")
                .build();

        // when-then
        assertThatThrownBy(() -> model.submit(new BatchRequest<>(List.of(CHAT_REQUEST))))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("httpParameters")
                .hasMessageContaining("batch");
        assertThat(mockHttpClient.requests()).isEmpty();
    }

    @Test
    void client_without_per_request_header_support_should_fail_only_when_headers_are_given() throws Exception {

        // given
        AnthropicClient client = mock(AnthropicClient.class, CALLS_REAL_METHODS);
        AnthropicCreateMessageRequest request =
                AnthropicCreateMessageRequest.builder().build();
        AnthropicCreateMessageOptions options = new AnthropicCreateMessageOptions(false);
        HttpParameters headers =
                HttpParameters.builder().customHeaders(Map.of("X-A", "1")).build();

        // when-then
        assertThatThrownBy(() -> client.createMessageWithRawResponse(request, headers))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("per-request custom HTTP headers");

        assertThat(client.createMessageWithRawResponseAsync(request, headers))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isExactlyInstanceOf(UnsupportedFeatureException.class);

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
        client.createMessage(request, options, headers, handler(futureResponse));
        assertThat(futureResponse)
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isExactlyInstanceOf(UnsupportedFeatureException.class);

        assertThat(firstError(client.createMessagePublisher(request, options, 16, headers)))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isExactlyInstanceOf(UnsupportedFeatureException.class);
    }

    @Test
    void client_spi_defaults_should_delegate_to_old_methods_without_headers() {

        // given
        AnthropicClient client = mock(AnthropicClient.class, CALLS_REAL_METHODS);
        AnthropicCreateMessageRequest request =
                AnthropicCreateMessageRequest.builder().build();
        AnthropicCreateMessageOptions options = new AnthropicCreateMessageOptions(false);
        StreamingChatResponseHandler handler = handler(new CompletableFuture<>());
        ParsedAndRawResponse response = mock(ParsedAndRawResponse.class);
        CompletableFuture<ParsedAndRawResponse> futureResponse = CompletableFuture.completedFuture(response);
        Flow.Publisher<ChatModelStreamingEvent> publisher = subscriber -> {};
        doReturn(response).when(client).createMessageWithRawResponse(request);
        doReturn(futureResponse).when(client).createMessageWithRawResponseAsync(request);
        doNothing().when(client).createMessage(request, options, handler);
        doReturn(publisher).when(client).createMessagePublisher(request, options, 16);

        for (HttpParameters noHeaders : Arrays.asList(
                null,
                HttpParameters.builder().build(),
                HttpParameters.builder().customHeaders(Map.of()).build())) {

            // when-then
            assertThat(client.createMessageWithRawResponse(request, noHeaders)).isSameAs(response);
            assertThat(client.createMessageWithRawResponseAsync(request, noHeaders)).isSameAs(futureResponse);
            client.createMessage(request, options, noHeaders, handler);
            assertThat(client.createMessagePublisher(request, options, 16, noHeaders))
                    .isSameAs(publisher);
        }

        verify(client, times(3)).createMessageWithRawResponse(request);
        verify(client, times(3)).createMessageWithRawResponseAsync(request);
        verify(client, times(3)).createMessage(request, options, handler);
        verify(client, times(3)).createMessagePublisher(request, options, 16);
    }

    @Test
    void default_client_async_should_return_failed_future_instead_of_throwing() {

        // given
        DefaultAnthropicClient client = DefaultAnthropicClient.builder()
                .httpClientBuilder(new MockHttpClientBuilder(MockHttpClient.thatAlwaysResponds(messageResponse())))
                .baseUrl("https://api.anthropic.com/v1/")
                .apiKey("test-key")
                .version("2023-06-01")
                .customHeaders(() -> {
                    throw new IllegalStateException("token refresh failed");
                })
                .build();

        // when
        CompletableFuture<ParsedAndRawResponse> future = client.createMessageWithRawResponseAsync(
                AnthropicCreateMessageRequest.builder().build(),
                HttpParameters.builder()
                        .customHeaders(Map.of("X-Correlation-Id", "123"))
                        .build());

        // then
        assertThat(future).failsWithin(5, SECONDS);
    }

    @Test
    void chat_model_should_merge_headers_of_default_request_parameters_with_per_request_headers() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(messageResponse());

        AnthropicChatModel model = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("claude-haiku-4-5-20251001")
                .defaultRequestParameters(DEFAULT_PARAMETERS_WITH_HEADERS)
                .build();

        // when
        model.chat(ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .customHeaders(Map.of("x-shared", "request"))
                .build());

        // then
        assertDefaultAndPerRequestHeadersMerged(mockHttpClient.request());
    }

    @Test
    void streaming_chat_model_should_merge_headers_of_default_request_parameters_with_per_request_headers()
            throws Exception {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(STREAMING_EVENTS);

        AnthropicStreamingChatModel model = AnthropicStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("claude-haiku-4-5-20251001")
                .defaultRequestParameters(DEFAULT_PARAMETERS_WITH_HEADERS)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(
                ChatRequest.builder()
                        .messages(UserMessage.from("Hello"))
                        .customHeaders(Map.of("x-shared", "request"))
                        .build(),
                handler(futureResponse));
        futureResponse.get(5, SECONDS);

        // then
        assertDefaultAndPerRequestHeadersMerged(mockHttpClient.request());
    }

    @Test
    void batch_chat_model_should_reject_headers_in_default_request_parameters_at_build_time() {

        assertThatThrownBy(() -> AnthropicBatchChatModel.builder()
                        .httpClientBuilder(new MockHttpClientBuilder(MockHttpClient.thatAlwaysResponds(messageResponse())))
                        .apiKey("test-key")
                        .modelName("claude-haiku-4-5-20251001")
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

    private static void assertDefaultAndPerRequestHeadersMerged(HttpRequest request) {
        Map<String, List<String>> headers = request.headers();
        assertThat(headers)
                .containsEntry("X-Default", List.of("d"))
                .containsEntry("x-shared", List.of("request"));
        assertThat(headers.keySet())
                .filteredOn(name -> name.equalsIgnoreCase("x-shared"))
                .containsExactly("x-shared");
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
                .containsEntry("x-api-key", List.of("test-key"));
        assertThat(headers.keySet())
                .filteredOn(name -> name.equalsIgnoreCase("x-shared"))
                .containsExactly("x-shared");
    }

    private static SuccessfulHttpResponse messageResponse() {
        return SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(MESSAGE_RESPONSE)
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
    void chat_model_should_send_per_request_user_agent_as_is_when_it_sends_none() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(messageResponse());

        AnthropicChatModel model = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("claude-haiku-4-5-20251001")
                .build();

        // when
        model.chat(USER_AGENT_REQUEST);

        // then
        assertThat(mockHttpClient.request().headers())
                .containsEntry("User-Agent", List.of("my-app/1.0"))
                .containsEntry("X-Correlation-Id", List.of("123"));
    }

    @Test
    void chat_model_should_append_per_request_user_agent_to_the_one_set_on_the_builder() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(messageResponse());

        AnthropicChatModel model = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("claude-haiku-4-5-20251001")
                .customHeaders(Map.of("User-Agent", "gateway-client/2.0"))
                .build();

        // when
        model.chat(USER_AGENT_REQUEST);

        // then
        assertThat(mockHttpClient.request().headers())
                .containsEntry("User-Agent", List.of("gateway-client/2.0 my-app/1.0"))
                .containsEntry("X-Correlation-Id", List.of("123"));
    }

    @Test
    void streaming_chat_model_should_append_per_request_user_agent_to_the_one_set_on_the_builder() throws Exception {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(STREAMING_EVENTS);

        AnthropicStreamingChatModel model = AnthropicStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("claude-haiku-4-5-20251001")
                .customHeaders(Map.of("User-Agent", "gateway-client/2.0"))
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(USER_AGENT_REQUEST, handler(futureResponse));
        futureResponse.get(5, SECONDS);

        // then
        assertThat(mockHttpClient.request().headers())
                .containsEntry("User-Agent", List.of("gateway-client/2.0 my-app/1.0"))
                .containsEntry("X-Correlation-Id", List.of("123"));
    }
}

