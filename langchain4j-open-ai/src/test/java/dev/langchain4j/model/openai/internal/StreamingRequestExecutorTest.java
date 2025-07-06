package dev.langchain4j.model.openai.internal;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class StreamingRequestExecutorTest {

    private static final String ERROR_MESSAGE =
            "{\"error\":{\"message\":\"Failed to call a function. Please adjust your prompt. " +
                    "See 'failed_generation' for more details.\",\"type\":\"invalid_request_error\"," +
                    "\"code\":\"tool_use_failed\"," +
                    "\"failed_generation\":\"Tool use failed: no tool can be called with name getCarsList\"," +
                    "\"status_code\":400}}";

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

        HttpRequest streamingHttpRequest = HttpRequest.builder()
                .method(GET)
                .url("http://does.not.matter")
                .build();

        StreamingRequestExecutor<ChatCompletionResponse> executor =
                new StreamingRequestExecutor<>(httpClient, streamingHttpRequest, ChatCompletionResponse.class);

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();

        executor.onPartialResponse(ignored -> {
                })
                .onError(futureError::complete)
                .execute();

        Throwable error = futureError.get(30, SECONDS);

        assertThat(error)
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage(ERROR_MESSAGE);
    }
}
