package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class OpenAiResponsesCustomHeadersTest {

    private static final String RESPONSE_BODY = """
            {
              "id": "resp_1",
              "model": "gpt-4o-mini",
              "status": "completed",
              "output": [
                {
                  "type": "message",
                  "role": "assistant",
                  "content": [
                    {
                      "type": "output_text",
                      "text": "Hi"
                    }
                  ]
                }
              ]
            }
            """;

    /**
     * Captures the outgoing request, so that its headers can be inspected.
     */
    private static class CapturingHttpClient implements HttpClient {

        private HttpRequest request;

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            this.request = request;
            return SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .headers(Map.of("Content-Type", List.of("application/json")))
                    .body(RESPONSE_BODY)
                    .build();
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            this.request = request;
        }
    }

    private static class CapturingHttpClientBuilder implements HttpClientBuilder {

        private final CapturingHttpClient httpClient = new CapturingHttpClient();

        @Override
        public Duration connectTimeout() {
            return null;
        }

        @Override
        public HttpClientBuilder connectTimeout(Duration connectTimeout) {
            return this;
        }

        @Override
        public Duration readTimeout() {
            return null;
        }

        @Override
        public HttpClientBuilder readTimeout(Duration readTimeout) {
            return this;
        }

        @Override
        public HttpClient build() {
            return httpClient;
        }
    }

    @Test
    void should_send_custom_headers_with_chat_model() {

        CapturingHttpClientBuilder httpClientBuilder = new CapturingHttpClientBuilder();

        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .httpClientBuilder(httpClientBuilder)
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .customHeaders(Map.of("Proxy-Authorization", "Basic dXNlcjpwYXNz"))
                .build();

        model.chat("Hello");

        assertThat(httpClientBuilder.httpClient.request.headers())
                .containsEntry("Proxy-Authorization", List.of("Basic dXNlcjpwYXNz"))
                .containsEntry("Authorization", List.of("Bearer test-key"));
    }

    @Test
    void should_send_custom_headers_with_streaming_chat_model() {

        CapturingHttpClientBuilder httpClientBuilder = new CapturingHttpClientBuilder();

        OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .httpClientBuilder(httpClientBuilder)
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .customHeaders(Map.of("Proxy-Authorization", "Basic dXNlcjpwYXNz"))
                .build();

        model.chat("Hello", new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {}

            @Override
            public void onError(Throwable error) {}
        });

        assertThat(httpClientBuilder.httpClient.request.headers())
                .containsEntry("Proxy-Authorization", List.of("Basic dXNlcjpwYXNz"))
                .containsEntry("Authorization", List.of("Bearer test-key"));
    }

    @Test
    void should_call_custom_headers_supplier_before_each_request() {

        CapturingHttpClientBuilder httpClientBuilder = new CapturingHttpClientBuilder();
        AtomicInteger callCount = new AtomicInteger();

        Supplier<Map<String, String>> customHeadersSupplier =
                () -> Map.of("X-Custom-Token", "token-" + callCount.incrementAndGet());

        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .httpClientBuilder(httpClientBuilder)
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .customHeaders(customHeadersSupplier)
                .build();

        model.chat("first");
        assertThat(httpClientBuilder.httpClient.request.headers()).containsEntry("X-Custom-Token", List.of("token-1"));

        model.chat("second");
        assertThat(httpClientBuilder.httpClient.request.headers()).containsEntry("X-Custom-Token", List.of("token-2"));

        assertThat(callCount).hasValue(2);
    }

    @Test
    void should_allow_custom_headers_to_override_default_headers() {

        CapturingHttpClientBuilder httpClientBuilder = new CapturingHttpClientBuilder();

        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .httpClientBuilder(httpClientBuilder)
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .customHeaders(Map.of("Authorization", "Bearer overridden"))
                .build();

        model.chat("Hello");

        assertThat(httpClientBuilder.httpClient.request.headers())
                .containsEntry("Authorization", List.of("Bearer overridden"));
    }

    @Test
    void should_work_without_custom_headers() {

        CapturingHttpClientBuilder httpClientBuilder = new CapturingHttpClientBuilder();

        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .httpClientBuilder(httpClientBuilder)
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .build();

        model.chat("Hello");

        assertThat(httpClientBuilder.httpClient.request.headers())
                .containsEntry("Authorization", List.of("Bearer test-key"));
    }

    @Test
    void should_handle_null_returned_by_custom_headers_supplier() {

        CapturingHttpClientBuilder httpClientBuilder = new CapturingHttpClientBuilder();

        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .httpClientBuilder(httpClientBuilder)
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .customHeaders(() -> null)
                .build();

        model.chat("Hello");

        assertThat(httpClientBuilder.httpClient.request.headers())
                .containsEntry("Authorization", List.of("Bearer test-key"));
    }
}
