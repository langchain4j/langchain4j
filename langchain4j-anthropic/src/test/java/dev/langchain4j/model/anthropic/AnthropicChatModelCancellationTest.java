package dev.langchain4j.model.anthropic;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AnthropicChatModelCancellationTest {

    // language=json
    private static final String SUCCESS_BODY = """
            {
              "id": "msg_123",
              "type": "message",
              "role": "assistant",
              "model": "claude-3-5-sonnet-20241022",
              "content": [{"type": "text", "text": "Hello!"}],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 10, "output_tokens": 5}
            }
            """;

    private final List<CompletableFuture<SuccessfulHttpResponse>> httpFutures = new CopyOnWriteArrayList<>();

    private HttpClient pendingHttpClient() {
        return new HttpClient() {
            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
                CompletableFuture<SuccessfulHttpResponse> future = new CompletableFuture<>();
                httpFutures.add(future);
                return future;
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static ChatModel model(HttpClient httpClient, int maxRetries) {
        return AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .modelName("claude-3-5-sonnet-20241022")
                .maxRetries(maxRetries)
                .build();
    }

    private static ChatRequest request() {
        return ChatRequest.builder().messages(UserMessage.from("hi")).build();
    }

    @ParameterizedTest
    @CsvSource({"0,true", "0,false", "2,true", "2,false"})
    void chatAsync_cancellation_is_propagated_to_the_http_request(int maxRetries, boolean mayInterruptIfRunning) {
        ChatModel model = model(pendingHttpClient(), maxRetries);

        CompletableFuture<ChatResponse> result = model.chatAsync(request());
        assertThat(httpFutures).hasSize(1);

        result.cancel(mayInterruptIfRunning);

        assertThat(result).isCancelled();
        assertThat(httpFutures.get(0)).isCancelled();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void chatAsync_cancellation_during_a_retry_is_propagated_to_the_current_attempt(boolean mayInterruptIfRunning) {
        HttpClient failingThenPending = new HttpClient() {
            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
                CompletableFuture<SuccessfulHttpResponse> future = new CompletableFuture<>();
                httpFutures.add(future);
                if (httpFutures.size() == 1) {
                    future.completeExceptionally(new HttpException(429, "overloaded"));
                }
                return future;
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                throw new UnsupportedOperationException();
            }
        };
        ChatModel model = model(failingThenPending, 2);

        CompletableFuture<ChatResponse> result = model.chatAsync(request());
        await().atMost(10, SECONDS).untilAsserted(() -> assertThat(httpFutures).hasSize(2));

        result.cancel(mayInterruptIfRunning);

        assertThat(httpFutures.get(1)).isCancelled();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void chatAsync_completes_normally_without_cancelling_the_http_request(boolean fails) throws Exception {
        ChatModel model = model(pendingHttpClient(), 0);

        CompletableFuture<ChatResponse> result = model.chatAsync(request());
        assertThat(httpFutures).hasSize(1);

        if (fails) {
            httpFutures.get(0).completeExceptionally(new HttpException(400, "bad request"));
            assertThatThrownBy(() -> result.get(10, SECONDS)).hasCauseInstanceOf(InvalidRequestException.class);
        } else {
            httpFutures
                    .get(0)
                    .complete(SuccessfulHttpResponse.builder()
                            .statusCode(200)
                            .headers(Map.of("content-type", List.of("application/json")))
                            .body(SUCCESS_BODY)
                            .build());
            assertThat(result.get(10, SECONDS).aiMessage().text()).isEqualTo("Hello!");
        }

        assertThat(httpFutures.get(0)).isNotCancelled();
        assertThat(result).isNotCancelled();
    }
}
