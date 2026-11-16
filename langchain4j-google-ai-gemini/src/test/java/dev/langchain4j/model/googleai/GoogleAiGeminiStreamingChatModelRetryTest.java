package dev.langchain4j.model.googleai;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Streaming retries through the real Gemini request pipeline: the mock HTTP client fails a request the way a real
 * client does for a non-2xx response, and the error goes through the provider's exception mapping.
 */
class GoogleAiGeminiStreamingChatModelRetryTest {

    private static final List<ServerSentEvent> EVENTS = List.of(new ServerSentEvent("message", """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"Hello"}]},"finishReason":"STOP"}],\
            "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}"""));

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

    private static StreamingChatModel model(MockHttpClient httpClient, Integer maxRetries) {
        return GoogleAiGeminiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost")
                .apiKey("test-key")
                .modelName("gemini-2.5-flash")
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
