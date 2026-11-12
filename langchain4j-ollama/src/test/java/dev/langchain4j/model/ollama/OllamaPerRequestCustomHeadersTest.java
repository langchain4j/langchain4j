package dev.langchain4j.model.ollama;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.HttpParameters;

class OllamaPerRequestCustomHeadersTest {

    private static final String CHAT_RESPONSE =
            """
            {
              "model": "llama3",
              "message": {"role": "assistant", "content": "Hello"},
              "done": true,
              "done_reason": "stop",
              "prompt_eval_count": 1,
              "eval_count": 1
            }
            """;

    private static final List<ServerSentEvent> STREAMING_EVENTS = List.of(
            new ServerSentEvent(
                    null, "{\"model\":\"llama3\",\"message\":{\"role\":\"assistant\",\"content\":\"Hi\"},\"done\":false}"),
            new ServerSentEvent(
                    null,
                    "{\"model\":\"llama3\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,"
                            + "\"done_reason\":\"stop\",\"prompt_eval_count\":1,\"eval_count\":1}"));

    private static final Map<String, String> MODEL_HEADERS = Map.of("X-Model-Header", "model", "X-Shared", "model");

    private static final ChatRequest CHAT_REQUEST = ChatRequest.builder()
            .messages(UserMessage.from("Hello"))
            .customHeaders(Map.of("x-shared", "request", "X-Correlation-Id", "123"))
            .build();

    @Test
    void chat_model_should_send_per_request_headers() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(chatResponse());

        OllamaChatModel model = OllamaChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
                .customHeaders(MODEL_HEADERS)
                .build();

        // when
        model.chat(CHAT_REQUEST);

        // then
        assertPerRequestHeadersSent(mockHttpClient.request());
    }

    @Test
    void per_request_header_should_replace_model_header_ignoring_case() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(chatResponse());

        OllamaChatModel model = OllamaChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
                .customHeaders(Map.of("Authorization", "Bearer model-token"))
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .customHeaders(Map.of("authorization", "Bearer request-token"))
                .build();

        // when
        model.chat(chatRequest);

        // then
        Map<String, List<String>> headers = mockHttpClient.request().headers();
        assertThat(headers).containsEntry("authorization", List.of("Bearer request-token"));
        assertThat(headers.keySet())
                .filteredOn(name -> name.equalsIgnoreCase("authorization"))
                .containsExactly("authorization");
    }

    @Test
    void chat_model_should_not_send_per_request_headers_of_previous_request() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(chatResponse());

        OllamaChatModel model = OllamaChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
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

        OllamaStreamingChatModel model = OllamaStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
                .customHeaders(MODEL_HEADERS)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(CHAT_REQUEST, new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });
        futureResponse.get(5, SECONDS);

        // then
        assertPerRequestHeadersSent(mockHttpClient.request());
    }

    @Test
    void chat_model_should_merge_headers_of_default_request_parameters_with_per_request_headers() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(chatResponse());

        OllamaChatModel model = OllamaChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
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

        OllamaStreamingChatModel model = OllamaStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
                .defaultRequestParameters(DEFAULT_PARAMETERS_WITH_HEADERS)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(REQUEST_OVERRIDING_DEFAULT_HEADER, new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });
        futureResponse.get(5, SECONDS);

        // then
        assertDefaultAndPerRequestHeadersMerged(mockHttpClient.request());
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
                .containsEntry("X-Correlation-Id", List.of("123"));
        assertThat(headers.keySet())
                .filteredOn(name -> name.equalsIgnoreCase("x-shared"))
                .containsExactly("x-shared");
    }

    private static SuccessfulHttpResponse chatResponse() {
        return SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(CHAT_RESPONSE)
                .build();
    }

    private static final ChatRequest USER_AGENT_REQUEST = ChatRequest.builder()
            .messages(UserMessage.from("Hello"))
            .customHeaders(Map.of("user-agent", "my-app/1.0", "X-Correlation-Id", "123"))
            .build();

    @Test
    void chat_model_should_send_per_request_user_agent_as_is_when_it_sends_none() {

        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(chatResponse());

        OllamaChatModel model = OllamaChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
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
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(chatResponse());

        OllamaChatModel model = OllamaChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
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

        OllamaStreamingChatModel model = OllamaStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
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

