package dev.langchain4j.model.mistralai;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
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
class MistralAiStreamingFimModelInBandErrorTest {

    private static final String MODEL = "codestral-latest";

    @Test
    void should_fail_with_provider_error_sent_as_server_sent_event() {

        // given
        List<ServerSentEvent> events = List.of(errorEvent(), doneEvent());

        MistralAiStreamingFimModel model = createModel(events);

        // when-then
        assertFailsWith(generate(model), "Chat completion failed: Service is too busy (type: ServerError, code: 429)");
    }

    @Test
    void should_fail_with_provider_error_sent_after_content() {

        // given
        List<ServerSentEvent> events = List.of(textEvent("def add(a, b):"), errorEvent(), doneEvent());

        MistralAiStreamingFimModel model = createModel(events);

        // when-then
        assertFailsWith(generate(model), "Chat completion failed: Service is too busy (type: ServerError, code: 429)");
    }

    @Test
    void should_fail_with_provider_error_reporting_type_and_code() {

        // given
        // vLLM/LiteLLM send a bare error object carrying message, type and code
        String errorChunk = """
                {"error":{"message":"Internal server error","type":"InternalServerError","code":500}}""";
        List<ServerSentEvent> events = List.of(textEvent("def add"), event(errorChunk), doneEvent());

        MistralAiStreamingFimModel model = createModel(events);

        // when-then
        assertFailsWith(
                generate(model),
                "Chat completion failed: Internal server error (type: InternalServerError, code: 500)");
    }

    @Test
    void should_stream_content_that_merely_mentions_error() throws Exception {

        // given
        // the streamed text merely contains the word "error": this must not be misread
        // as an in-band provider error
        List<ServerSentEvent> events =
                List.of(textEvent("no "), textEvent("\"error\" here"), finishEvent("stop"), doneEvent());

        MistralAiStreamingFimModel model = createModel(events);

        // when
        Response<String> response = generate(model).get(10, SECONDS);

        // then
        assertThat(response.content()).isEqualTo("no \"error\" here");
    }

    // -- Helper methods --

    private static MistralAiStreamingFimModel createModel(List<ServerSentEvent> events) {
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(events);
        return MistralAiStreamingFimModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName(MODEL)
                .build();
    }

    private static CompletableFuture<Response<String>> generate(MistralAiStreamingFimModel model) {
        CompletableFuture<Response<String>> future = new CompletableFuture<>();
        model.generate("def add", new StreamingResponseHandler<>() {

            @Override
            public void onNext(String token) {}

            @Override
            public void onComplete(Response<String> response) {
                future.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    private static void assertFailsWith(CompletableFuture<Response<String>> future, String expectedMessage) {
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
