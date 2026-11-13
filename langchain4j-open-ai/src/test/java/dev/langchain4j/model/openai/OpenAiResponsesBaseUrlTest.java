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
import org.junit.jupiter.api.Test;

class OpenAiResponsesBaseUrlTest {

    private static final String AZURE_BASE_URL = "http://xxxxx.openai.azure.com/openai/v1/";
    private static final String JOINED_RESPONSES_URL = "http://xxxxx.openai.azure.com/openai/v1/responses";
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

    @Test
    void should_join_a_base_url_that_ends_with_a_slash() {
        CapturingHttpClientBuilder httpClientBuilder = new CapturingHttpClientBuilder();

        OpenAiResponsesChatModel model = chatModel(httpClientBuilder, AZURE_BASE_URL);

        model.chat("Hello");

        assertThat(httpClientBuilder.httpClient.request.url()).isEqualTo(JOINED_RESPONSES_URL);
    }

    @Test
    void should_join_a_base_url_that_does_not_end_with_a_slash() {
        CapturingHttpClientBuilder httpClientBuilder = new CapturingHttpClientBuilder();

        OpenAiResponsesChatModel model = chatModel(httpClientBuilder, "http://xxxxx.openai.azure.com/openai/v1");

        model.chat("Hello");

        assertThat(httpClientBuilder.httpClient.request.url()).isEqualTo(JOINED_RESPONSES_URL);
    }

    @Test
    void should_join_a_base_url_that_ends_with_a_slash_when_streaming() {
        CapturingHttpClientBuilder httpClientBuilder = new CapturingHttpClientBuilder();

        OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .httpClientBuilder(httpClientBuilder)
                .baseUrl(AZURE_BASE_URL)
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .build();

        model.chat("Hello", new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {}

            @Override
            public void onError(Throwable error) {}
        });

        assertThat(httpClientBuilder.httpClient.request.url()).isEqualTo(JOINED_RESPONSES_URL);
    }

    private static OpenAiResponsesChatModel chatModel(HttpClientBuilder httpClientBuilder, String baseUrl) {
        return OpenAiResponsesChatModel.builder()
                .httpClientBuilder(httpClientBuilder)
                .baseUrl(baseUrl)
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .build();
    }

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
}
