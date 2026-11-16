package dev.langchain4j.model.openai;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

/**
 * Streaming retries through the real OpenAI request pipeline: the mock HTTP client fails a request the way a real
 * client does for a non-2xx response, and the error goes through the provider's exception mapping.
 */
class OpenAiStreamingChatModelRetryTest {

    private static final List<ServerSentEvent> EVENTS = List.of(
            new ServerSentEvent(null, """
                    {"id":"1","object":"chat.completion.chunk","created":1,"model":"gpt-4o-mini",\
                    "choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finish_reason":null}]}"""), new ServerSentEvent(null, """
                    {"id":"1","object":"chat.completion.chunk","created":1,"model":"gpt-4o-mini",\
                    "choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""), new ServerSentEvent(null, "[DONE]"));

    @Test
    void should_retry_retriable_http_error() throws Exception {

        // given
        MockHttpClient httpClient = MockHttpClient.thatFailsAndThenResponds(1, new HttpException(503, "busy"), EVENTS);
        StreamingChatModel model = model(httpClient, 1);

        // when
        ChatResponse response = chat(model).get(10, SECONDS);

        // then
        assertThat(response.aiMessage().text()).isEqualTo("Hello");
        assertThat(httpClient.requests()).hasSize(2);
    }

    @Test
    void should_not_retry_by_default() {

        // given
        MockHttpClient httpClient = MockHttpClient.thatFailsAndThenResponds(1, new HttpException(503, "busy"), EVENTS);
        StreamingChatModel model = model(httpClient, null);

        // when-then
        assertThat(model.maxRetries()).isZero();
        assertThatThrownBy(() -> chat(model).get(10, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(InternalServerException.class);
        assertThat(httpClient.requests()).hasSize(1);
    }

    @Test
    void should_not_retry_non_retriable_http_error() {

        // given
        MockHttpClient httpClient =
                MockHttpClient.thatFailsAndThenResponds(1, new HttpException(401, "unauthorized"), EVENTS);
        StreamingChatModel model = model(httpClient, 2);

        // when-then
        assertThatThrownBy(() -> chat(model).get(10, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(AuthenticationException.class);
        assertThat(httpClient.requests()).hasSize(1);
    }

    private static StreamingChatModel model(MockHttpClient httpClient, Integer maxRetries) {
        return OpenAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .maxRetries(maxRetries)
                .build();
    }

    private static CompletableFuture<ChatResponse> chat(StreamingChatModel model) {
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        model.chat("hi", new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });
        return future;
    }
}
