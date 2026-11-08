package dev.langchain4j.model.anthropic;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Deterministic coverage of the non-blocking retry on {@link AnthropicChatModel#doChatAsync}: a second provider
 * (different API shape from OpenAI, same {@code HttpClient} + shared {@code RetryUtils} plumbing) driven by a mock
 * HTTP client, showing the design generalizes beyond OpenAI.
 */
class AnthropicChatModelAsyncRetryTest {

    // language=json
    private static final String SUCCESS_BODY =
            """
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

    private static HttpClient httpClient(Supplier<CompletableFuture<SuccessfulHttpResponse>> responder) {
        return new HttpClient() {
            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
                return responder.get();
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static SuccessfulHttpResponse okResponse() {
        return SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("content-type", List.of("application/json")))
                .body(SUCCESS_BODY)
                .build();
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

    @Test
    void chatAsync_retries_a_retriable_failure_and_then_succeeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = model(
                httpClient(() -> calls.incrementAndGet() <= 2
                        ? CompletableFuture.failedFuture(new HttpException(429, "overloaded"))
                        : CompletableFuture.completedFuture(okResponse())),
                2);

        ChatResponse response = model.chatAsync(request()).get(30, SECONDS);

        assertThat(response.aiMessage().text()).isEqualTo("Hello!");
        assertThat(calls).as("two retriable (429) failures + one success").hasValue(3);
    }

    @Test
    void chatAsync_does_not_retry_a_non_retriable_failure() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = model(
                httpClient(() -> {
                    calls.incrementAndGet();
                    return CompletableFuture.failedFuture(new HttpException(401, "unauthorized"));
                }),
                5);

        // a 401 maps to AuthenticationException (NonRetriableException) - it must fail immediately, no retries
        assertThatThrownBy(() -> model.chatAsync(request()).get(10, SECONDS))
                .hasCauseInstanceOf(AuthenticationException.class);
        assertThat(calls).hasValue(1);
    }
}
