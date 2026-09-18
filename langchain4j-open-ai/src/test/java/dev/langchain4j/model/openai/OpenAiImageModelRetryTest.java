package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OpenAiImageModelRetryTest {

    /**
     * Fails every request, so that the number of attempts can be counted.
     */
    private static class FailingHttpClient implements HttpClient {

        private final AtomicInteger attempts = new AtomicInteger();

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            attempts.incrementAndGet();
            throw new HttpException(500, "server error");
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FailingHttpClientBuilder implements HttpClientBuilder {

        private final FailingHttpClient httpClient = new FailingHttpClient();

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

    private static OpenAiImageModel model(FailingHttpClientBuilder httpClientBuilder, int maxRetries) {
        return OpenAiImageModel.builder()
                .httpClientBuilder(httpClientBuilder)
                .baseUrl("http://localhost:1/v1")
                .apiKey("test-api-key")
                .modelName("gpt-image-1")
                .maxRetries(maxRetries)
                .build();
    }

    @Test
    void should_retry_image_generation() {
        FailingHttpClientBuilder httpClientBuilder = new FailingHttpClientBuilder();

        assertThatThrownBy(() -> model(httpClientBuilder, 2).generate("banana"))
                .isInstanceOf(InternalServerException.class);

        // the initial attempt plus two retries
        assertThat(httpClientBuilder.httpClient.attempts).hasValue(3);
    }

    @Test
    void should_retry_image_editing() {
        FailingHttpClientBuilder httpClientBuilder = new FailingHttpClientBuilder();

        assertThatThrownBy(() -> model(httpClientBuilder, 2).edit(Image.builder().base64Data("aGk=").build(), "banana"))
                .isInstanceOf(InternalServerException.class);

        assertThat(httpClientBuilder.httpClient.attempts).hasValue(3);
    }

    @Test
    void should_not_retry_image_generation_when_max_retries_is_zero() {
        FailingHttpClientBuilder httpClientBuilder = new FailingHttpClientBuilder();

        assertThatThrownBy(() -> model(httpClientBuilder, 0).generate("banana"))
                .isInstanceOf(InternalServerException.class);

        assertThat(httpClientBuilder.httpClient.attempts).hasValue(1);
    }
}
