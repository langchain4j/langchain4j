package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Regression for <a href="https://github.com/langchain4j/langchain4j/issues/6707">#6707</a>:
 * OpenAI-compatible servers report post-HTTP-200 failures as SSE data chunks with an {@code error}
 * object. Those must reach {@code onError}, not {@code onCompleteResponse}.
 */
class OpenAiStreamingChatModelMidStreamErrorTest {

    private static final String CONTENT_CHUNK =
            "{\"id\":\"x\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"gpt-4o-mini\","
                    + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Half an ans\"},\"finish_reason\":null}]}";

    private static final String BARE_ERROR =
            "{\"error\":{\"message\":\"Internal server error\",\"type\":\"InternalServerError\",\"param\":null,\"code\":500}}";

    private static final String ERROR_CHUNK =
            "{\"id\":\"cmpl-abc123\",\"object\":\"chat.completion.chunk\",\"created\":1234567890,"
                    + "\"model\":\"gpt-4o-mini\",\"provider\":\"test\","
                    + "\"error\":{\"code\":\"server_error\",\"message\":\"Provider disconnected unexpectedly\"},"
                    + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"\"},\"finish_reason\":\"error\"}]}";

    @ParameterizedTest
    @ValueSource(strings = {"bare", "chunk"})
    void should_call_onError_for_mid_stream_error_payload(String shape) throws Exception {
        String errorData = "bare".equals(shape) ? BARE_ERROR : ERROR_CHUNK;
        List<ServerSentEvent> events = List.of(
                new ServerSentEvent(null, CONTENT_CHUNK),
                new ServerSentEvent(null, errorData),
                new ServerSentEvent(null, "[DONE]"));
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(events);

        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .build();

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();
        AtomicReference<ChatResponse> completed = new AtomicReference<>();
        StringBuilder partials = new StringBuilder();

        model.chat("Hi", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                partials.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                completed.set(completeResponse);
                futureError.completeExceptionally(
                        new AssertionError("expected onError, got onCompleteResponse: " + completeResponse));
            }

            @Override
            public void onError(Throwable error) {
                futureError.complete(error);
            }
        });

        Throwable error = futureError.get(5, TimeUnit.SECONDS);

        assertThat(partials.toString()).isEqualTo("Half an ans");
        assertThat(completed.get()).isNull();
        assertThat(error).isInstanceOf(OpenAiStreamingException.class);
        OpenAiStreamingException streamingError = (OpenAiStreamingException) error;
        if ("bare".equals(shape)) {
            assertThat(streamingError.getMessage()).isEqualTo("Internal server error");
            assertThat(streamingError.type()).isEqualTo("InternalServerError");
            assertThat(streamingError.code()).isEqualTo("500");
        } else {
            assertThat(streamingError.getMessage()).isEqualTo("Provider disconnected unexpectedly");
            assertThat(streamingError.code()).isEqualTo("server_error");
        }
    }

    @Test
    void should_still_complete_successful_streams() throws Exception {
        List<ServerSentEvent> events = List.of(
                new ServerSentEvent(null, CONTENT_CHUNK),
                new ServerSentEvent(
                        null,
                        "{\"id\":\"x\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"gpt-4o-mini\","
                                + "\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}"),
                new ServerSentEvent(null, "[DONE]"));
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(events);

        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        model.chat("Hi", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        ChatResponse response = futureResponse.get(5, TimeUnit.SECONDS);
        assertThat(response.aiMessage().text()).isEqualTo("Half an ans");
    }
}
