package dev.langchain4j.model.mistralai;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

/**
 * Some OpenAI-compatible servers deployed in front of Mistral (LiteLLM, vLLM gateways, OpenRouter, etc.)
 * report errors with HTTP 200 inside the stream, as chunks carrying a top-level "error" object,
 * instead of with an error status code. Such chunks must fail the stream,
 * not be swallowed and reported as a short successful answer.
 * @see <a href="https://github.com/langchain4j/langchain4j/issues/7239">issue #7239</a>
 */
class MistralAiStreamingChatModelInBandErrorTest {

    private static final String MODEL = "mistral-small-latest";

    @Test
    void should_fail_with_provider_error_sent_as_server_sent_event() {

        // given
        List<ServerSentEvent> events = List.of(errorEvent(), doneEvent());

        StreamingChatModel model = createModel(events);

        // when-then
        assertFailsWith(chat(model), "Chat completion failed: Service is too busy (type: ServerError, code: 429)");
    }

    @Test
    void should_fail_with_provider_error_sent_after_content() {

        // given
        List<ServerSentEvent> events = List.of(textEvent("Hel"), errorEvent(), doneEvent());

        StreamingChatModel model = createModel(events);

        // when-then
        assertFailsWith(chat(model), "Chat completion failed: Service is too busy (type: ServerError, code: 429)");
    }

    @Test
    void should_fail_with_provider_error_reporting_type_and_code() {

        // given
        // vLLM/LiteLLM send a bare error object carrying message, type and code
        String errorChunk = """
                {"error":{"message":"Internal server error","type":"InternalServerError","code":500}}""";
        List<ServerSentEvent> events = List.of(textEvent("Hel"), event(errorChunk), doneEvent());

        StreamingChatModel model = createModel(events);

        // when-then
        assertFailsWith(
                chat(model), "Chat completion failed: Internal server error (type: InternalServerError, code: 500)");
    }

    @Test
    void should_fail_with_provider_error_chunk_that_also_carries_choices() {

        // given
        // OpenRouter reports mid-stream errors as a regular chunk with a top-level "error" object
        // alongside "choices", with finish_reason "error"
        String errorChunk = """
                {"id":"abc123","model":"%s","error":{"code":"server_error","message":"Provider disconnected \
                unexpectedly"},"choices":[{"index":0,"delta":{"content":""},"finish_reason":"error"}]}""".formatted(MODEL);
        List<ServerSentEvent> events = List.of(textEvent("Hel"), event(errorChunk), doneEvent());

        StreamingChatModel model = createModel(events);

        // when-then
        assertFailsWith(chat(model), "Chat completion failed: Provider disconnected unexpectedly (code: server_error)");
    }

    @Test
    void should_stream_content_that_merely_mentions_error() throws Exception {

        // given
        // the streamed text merely contains the word "error": this must not be misread
        // as an in-band provider error
        List<ServerSentEvent> events =
                List.of(textEvent("No "), textEvent("\"error\" occurred"), finishEvent("stop"), doneEvent());

        StreamingChatModel model = createModel(events);

        // when
        ChatResponse chatResponse = chat(model).get(10, SECONDS);

        // then
        assertThat(chatResponse.aiMessage().text()).isEqualTo("No \"error\" occurred");
        assertThat(chatResponse.metadata().finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_stream_when_error_key_is_nested_and_not_top_level() throws Exception {

        // given
        // an "error" key nested below the top level (here inside "usage") is not an in-band provider error
        String chunk = """
                {"id":"abc123","model":"%s","choices":[{"index":0,"delta":{"content":[{"type":"text",\
                "text":"All good"}]}}],"usage":{"prompt_tokens":1,"error":{"message":"nested"}}}""".formatted(MODEL);
        List<ServerSentEvent> events = List.of(event(chunk), finishEvent("stop"), doneEvent());

        StreamingChatModel model = createModel(events);

        // when
        ChatResponse chatResponse = chat(model).get(10, SECONDS);

        // then
        assertThat(chatResponse.aiMessage().text()).isEqualTo("All good");
        assertThat(chatResponse.metadata().finishReason()).isEqualTo(FinishReason.STOP);
    }

    // -- Helper methods --

    private static MistralAiStreamingChatModel createModel(List<ServerSentEvent> events) {
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(events);
        return MistralAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName(MODEL)
                .build();
    }

    private static CompletableFuture<ChatResponse> chat(StreamingChatModel model) {
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        model.chat("hi", new dev.langchain4j.model.chat.response.StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {}

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

    private static void assertFailsWith(CompletableFuture<ChatResponse> future, String expectedMessage) {
        assertThat(future)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isExactlyInstanceOf(InternalServerException.class)
                .withMessage(expectedMessage);
    }

    private static ServerSentEvent textEvent(String text) {
        String escaped = text.replace("\"", "\\\"");
        return event("""
                {"id":"abc123","model":"%s","choices":[{"index":0,"delta":{"content":[{"type":"text","text":"%s"}]}}]}""".formatted(MODEL, escaped));
    }

    private static ServerSentEvent finishEvent(String reason) {
        return event("""
                {"id":"abc123","model":"%s","choices":[{"index":0,"delta":{},"finish_reason":"%s"}],"usage":{"prompt_tokens":10,"completion_tokens":20,"total_tokens":30}}""".formatted(MODEL, reason));
    }

    private static ServerSentEvent errorEvent() {
        return event("""
                {"error":{"message":"Service is too busy","type":"ServerError","code":429}}""");
    }

    private static ServerSentEvent doneEvent() {
        return new ServerSentEvent(null, "[DONE]");
    }

    private static ServerSentEvent event(String data) {
        return new ServerSentEvent(null, data);
    }
}
