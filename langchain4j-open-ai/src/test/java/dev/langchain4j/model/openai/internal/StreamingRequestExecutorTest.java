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
    void should_fail_on_bare_error_data_chunk() throws Exception {
        // vLLM / LiteLLM: data is only the error object (no event: error)
        String bareError =
                "{\"error\":{\"message\":\"Internal server error\",\"type\":\"InternalServerError\",\"param\":null,\"code\":500}}";

        HttpClient httpClient = new HttpClient() {

            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) {
                throw new IllegalStateException("this method should not be called");
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                listener.onEvent(
                        new ServerSentEvent(null, "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Half\"}}]}"));
                listener.onEvent(new ServerSentEvent(null, bareError));
                listener.onEvent(new ServerSentEvent(null, "[DONE]"));
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

        assertThat(error)
                .isInstanceOf(dev.langchain4j.model.openai.OpenAiStreamingException.class)
                .hasMessage("Internal server error");
        assertThat(((dev.langchain4j.model.openai.OpenAiStreamingException) error).type())
                .isEqualTo("InternalServerError");
        assertThat(((dev.langchain4j.model.openai.OpenAiStreamingException) error).code())
                .isEqualTo("500");
        assertThat(futureComplete).isNotCompleted();
    }

    @Test
    void should_fail_on_error_field_in_completion_chunk() throws Exception {
        // OpenRouter: regular chat.completion.chunk with top-level error + finish_reason error
        String errorChunk =
                "{\"id\":\"cmpl-abc123\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"x\","
                        + "\"error\":{\"code\":\"server_error\",\"message\":\"Provider disconnected unexpectedly\"},"
                        + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"\"},\"finish_reason\":\"error\"}]}";

        HttpClient httpClient = new HttpClient() {

            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) {
                throw new IllegalStateException("this method should not be called");
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                listener.onEvent(
                        new ServerSentEvent(null, "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Half\"}}]}"));
                listener.onEvent(new ServerSentEvent(null, errorChunk));
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

        assertThat(error)
                .isInstanceOf(dev.langchain4j.model.openai.OpenAiStreamingException.class)
                .hasMessage("Provider disconnected unexpectedly");
        assertThat(((dev.langchain4j.model.openai.OpenAiStreamingException) error).code())
                .isEqualTo("server_error");
        assertThat(futureComplete).isNotCompleted();
    }
}
