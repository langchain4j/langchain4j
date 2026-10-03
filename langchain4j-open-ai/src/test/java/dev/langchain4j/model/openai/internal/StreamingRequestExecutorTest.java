package dev.langchain4j.model.openai.internal;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class StreamingRequestExecutorTest {

    private static final String ERROR_MESSAGE =
            "{\"error\":{\"message\":\"Failed to call a function. Please adjust your prompt. "
                    + "See 'failed_generation' for more details.\",\"type\":\"invalid_request_error\","
                    + "\"code\":\"tool_use_failed\","
                    + "\"failed_generation\":\"Tool use failed: no tool can be called with name getCarsList\","
                    + "\"status_code\":400}}";

    @Test
    void should_process_streaming_error() throws Exception {

        HttpClient httpClient = new HttpClient() {

            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) {
                throw new IllegalStateException("this method should not be called");
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                listener.onEvent(new ServerSentEvent("error", ERROR_MESSAGE));
            }
        };

        HttpRequest streamingHttpRequest =
                HttpRequest.builder().method(GET).url("http://does.not.matter").build();

        StreamingRequestExecutor<ChatCompletionResponse> executor =
                new StreamingRequestExecutor<>(httpClient, streamingHttpRequest, ChatCompletionResponse.class);

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();

        executor.onPartialResponse(ignored -> {}).onError(futureError::complete).execute();

        Throwable error = futureError.get(30, SECONDS);

        assertThat(error).isExactlyInstanceOf(RuntimeException.class).hasMessage(ERROR_MESSAGE);
    }

    @Test
    void should_not_call_onComplete_when_error_event_received() throws Exception {

        HttpClient httpClient = new HttpClient() {

            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) {
                throw new IllegalStateException("this method should not be called");
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                listener.onEvent(new ServerSentEvent("error", ERROR_MESSAGE));
                listener.onClose();
            }
        };

        HttpRequest streamingHttpRequest =
                HttpRequest.builder().method(GET).url("http://does.not.matter").build();

        StreamingRequestExecutor<ChatCompletionResponse> executor =
                new StreamingRequestExecutor<>(httpClient, streamingHttpRequest, ChatCompletionResponse.class);

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();
        CompletableFuture<Void> futureComplete = new CompletableFuture<>();

        executor.onPartialResponse(ignored -> {})
                .onComplete(() -> futureComplete.complete(null))
                .onError(futureError::complete)
                .execute();

        Throwable error = futureError.get(30, SECONDS);

        assertThat(error).isExactlyInstanceOf(RuntimeException.class).hasMessage(ERROR_MESSAGE);

        assertThat(futureComplete).isNotDone();
    }

    @Test
    void should_not_call_onComplete_when_json_parsing_failed() throws Exception {

        HttpClient httpClient = new HttpClient() {

            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) {
                throw new IllegalStateException("this method should not be called");
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                listener.onEvent(new ServerSentEvent(null, "not a valid json"));
                listener.onClose();
            }
        };

        HttpRequest streamingHttpRequest =
                HttpRequest.builder().method(GET).url("http://does.not.matter").build();

        StreamingRequestExecutor<ChatCompletionResponse> executor =
                new StreamingRequestExecutor<>(httpClient, streamingHttpRequest, ChatCompletionResponse.class);

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();
        CompletableFuture<Void> futureComplete = new CompletableFuture<>();

        executor.onPartialResponse(ignored -> {})
                .onComplete(() -> futureComplete.complete(null))
                .onError(futureError::complete)
                .execute();

        Throwable error = futureError.get(30, SECONDS);

        assertThat(error).isNotNull();

        assertThat(futureComplete).isNotDone();
    }

    @Test
    void should_not_call_onComplete_when_stream_fails_before_close() throws Exception {

        HttpClient httpClient = new HttpClient() {

            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) {
                throw new IllegalStateException("this method should not be called");
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                listener.onError(new IllegalStateException("connection reset"));
                listener.onClose();
            }
        };

        HttpRequest streamingHttpRequest =
                HttpRequest.builder().method(GET).url("http://does.not.matter").build();

        StreamingRequestExecutor<ChatCompletionResponse> executor =
                new StreamingRequestExecutor<>(httpClient, streamingHttpRequest, ChatCompletionResponse.class);

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();
        CompletableFuture<Void> futureComplete = new CompletableFuture<>();

        executor.onPartialResponse(ignored -> {})
                .onComplete(() -> futureComplete.complete(null))
                .onError(futureError::complete)
                .execute();

        Throwable error = futureError.get(30, SECONDS);

        assertThat(error).isExactlyInstanceOf(IllegalStateException.class).hasMessage("connection reset");

        assertThat(futureComplete).isNotDone();
    }

    @Test
    void should_call_onComplete_when_stream_completed_successfully() throws Exception {

        HttpClient httpClient = new HttpClient() {

            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) {
                throw new IllegalStateException("this method should not be called");
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                listener.onEvent(new ServerSentEvent(null, "{\"id\":\"chatcmpl-123\"}"));
                listener.onClose();
            }
        };

        HttpRequest streamingHttpRequest =
                HttpRequest.builder().method(GET).url("http://does.not.matter").build();

        StreamingRequestExecutor<ChatCompletionResponse> executor =
                new StreamingRequestExecutor<>(httpClient, streamingHttpRequest, ChatCompletionResponse.class);

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();
        CompletableFuture<Void> futureComplete = new CompletableFuture<>();

        executor.onPartialResponse(ignored -> {})
                .onComplete(() -> futureComplete.complete(null))
                .onError(futureError::complete)
                .execute();

        futureComplete.get(30, SECONDS);

        assertThat(futureError).isNotDone();
    }
}
