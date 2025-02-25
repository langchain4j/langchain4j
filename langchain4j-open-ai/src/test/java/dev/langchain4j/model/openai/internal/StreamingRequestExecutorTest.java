package dev.langchain4j.model.openai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class StreamingRequestExecutorTest {
    private static final String ERROR_MESSAGE =
            "{\"error\":{\"message\":\"Failed to call a function. Please adjust your prompt. See 'failed_generation' for more details.\",\"type\":\"invalid_request_error\",\"code\":\"tool_use_failed\",\"failed_generation\":\"Tool use failed: no tool can be called with name getCarsList\",\"status_code\":400}}";

    @Test
    void should_process_streaming_error() {
        HttpClient httpClient = new HttpClient() {
            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) throws HttpException, RuntimeException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                listener.onEvent(new ServerSentEvent("error", ERROR_MESSAGE));
            }
        };
        Consumer<ChatCompletionResponse> partialResponseHandler = new Consumer<ChatCompletionResponse>() {
            @Override
            public void accept(ChatCompletionResponse t) {}
        };
        HttpRequest streamingHttpRequest = HttpRequest.builder()
                .url("http://localhost:8080/sse")
                .method(HttpMethod.GET)
                .build();
        StreamingRequestExecutor executor =
                new StreamingRequestExecutor(httpClient, streamingHttpRequest, ChatCompletionResponse.class);
        executor.onPartialResponse(partialResponseHandler)
                .onError(error -> {
                    assertThat(error instanceof RuntimeException);
                    assertThat(ERROR_MESSAGE.equals(error.getMessage()));
                })
                .execute();
    }
}
