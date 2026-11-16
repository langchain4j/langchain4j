package dev.langchain4j.model.anthropic;

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
 * Streaming retries through the real Anthropic request pipeline: the mock HTTP client fails a request the way a real
 * client does for a non-2xx response, and the error goes through the provider's exception mapping.
 */
class AnthropicStreamingChatModelRetryTest {

    private static final List<ServerSentEvent> EVENTS = List.of(
            new ServerSentEvent("message_start", """
                    {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant",\
                    "model":"claude-opus-5-5","usage":{"input_tokens":1,"output_tokens":0}}}"""),
            new ServerSentEvent("content_block_start", """
                    {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}"""),
            new ServerSentEvent("content_block_delta", """
                    {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}"""),
            new ServerSentEvent("content_block_stop", """
                    {"type":"content_block_stop","index":0}"""),
            new ServerSentEvent("message_delta", """
                    {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":1}}"""),
            new ServerSentEvent("message_stop", """
                    {"type":"message_stop"}"""));

    @Test
    void should_retry_retriable_http_error() throws Exception {

        // given
        MockHttpClient httpClient =
                MockHttpClient.thatFailsAndThenResponds(1, new HttpException(529, "overloaded"), EVENTS);
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
        MockHttpClient httpClient =
                MockHttpClient.thatFailsAndThenResponds(1, new HttpException(529, "overloaded"), EVENTS);
        StreamingChatModel model = model(httpClient, null);

        // when-then
        assertThat(model.maxRetries()).isZero();
        assertThatThrownBy(() -> chat(model).get(10, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(InternalServerException.class);
        assertThat(httpClient.requests()).hasSize(1);
    }

    private static StreamingChatModel model(MockHttpClient httpClient, Integer maxRetries) {
        return AnthropicStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .modelName("claude-opus-5-5")
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
