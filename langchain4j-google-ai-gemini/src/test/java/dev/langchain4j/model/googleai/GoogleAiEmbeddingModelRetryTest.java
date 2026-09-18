package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GoogleAiEmbeddingModelRetryTest {

    private static final String BATCH_EMBED_RESPONSE = "{\"embeddings\":[{\"values\":[0.1,0.2,0.3]}]}";

    private static class FailingHttpClient implements HttpClient {

        private final AtomicInteger attempts = new AtomicInteger();
        private final int failuresBeforeSuccess;

        private FailingHttpClient(int failuresBeforeSuccess) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        static FailingHttpClient alwaysFailing() {
            return new FailingHttpClient(Integer.MAX_VALUE);
        }

        static FailingHttpClient failingOnce() {
            return new FailingHttpClient(1);
        }

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            if (attempts.incrementAndGet() > failuresBeforeSuccess) {
                return SuccessfulHttpResponse.builder()
                        .statusCode(200)
                        .body(BATCH_EMBED_RESPONSE)
                        .build();
            }
            throw new HttpException(500, "server error");
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            throw new UnsupportedOperationException();
        }
    }

    private static GoogleAiEmbeddingModel model(FailingHttpClient httpClient, int maxRetries) {
        return GoogleAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost")
                .apiKey("dummy")
                .modelName("gemini-embedding-001")
                .maxRetries(maxRetries)
                .build();
    }

    @Test
    void should_not_retry_embedding_when_max_retries_is_zero() {
        FailingHttpClient httpClient = FailingHttpClient.alwaysFailing();

        assertThatThrownBy(() -> model(httpClient, 0).embed("hello")).isInstanceOf(InternalServerException.class);

        assertThat(httpClient.attempts).hasValue(1);
    }

    @Test
    void should_not_retry_batch_embedding_when_max_retries_is_zero() {
        FailingHttpClient httpClient = FailingHttpClient.alwaysFailing();

        assertThatThrownBy(() -> model(httpClient, 0).embedAll(List.of(TextSegment.from("hello"))))
                .isInstanceOf(InternalServerException.class);

        assertThat(httpClient.attempts).hasValue(1);
    }

    @Test
    void should_not_retry_embedding_request_when_max_retries_is_zero() {
        FailingHttpClient httpClient = FailingHttpClient.alwaysFailing();
        EmbeddingRequest request = EmbeddingRequest.builder()
                .textSegment(TextSegment.from("hello"))
                .build();

        assertThatThrownBy(() -> model(httpClient, 0).embed(request)).isInstanceOf(InternalServerException.class);

        assertThat(httpClient.attempts).hasValue(1);
    }

    @Test
    void should_return_embeddings_when_a_retry_succeeds() {
        FailingHttpClient httpClient = FailingHttpClient.failingOnce();

        Response<List<Embedding>> response = model(httpClient, 1).embedAll(List.of(TextSegment.from("hello")));

        assertThat(httpClient.attempts).hasValue(2);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).vector()).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void should_retry_batch_embedding() {
        FailingHttpClient httpClient = FailingHttpClient.alwaysFailing();

        assertThatThrownBy(() -> model(httpClient, 1).embedAll(List.of(TextSegment.from("hello"))))
                .isInstanceOf(InternalServerException.class);

        assertThat(httpClient.attempts).hasValue(2);
    }
}
