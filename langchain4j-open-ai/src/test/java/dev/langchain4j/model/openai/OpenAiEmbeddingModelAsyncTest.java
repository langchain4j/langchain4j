package dev.langchain4j.model.openai;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic coverage of {@link OpenAiEmbeddingModel#doEmbedAsync}'s cancellation semantics: cancelling the
 * caller-facing future must abort the in-flight HTTP call, propagating through the {@code allOf} aggregate that
 * composes the per-batch futures.
 */
class OpenAiEmbeddingModelAsyncTest {

    @Test
    void cancelling_embedAsync_future_aborts_the_in_flight_http_call() {

        // an HTTP client that never responds, exposing its in-flight future so we can assert it gets cancelled
        CompletableFuture<SuccessfulHttpResponse> httpFuture = new CompletableFuture<>();
        HttpClient neverRespondingClient = new HttpClient() {
            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
                return httpFuture;
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                throw new UnsupportedOperationException();
            }
        };

        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(neverRespondingClient))
                .modelName("text-embedding-3-small")
                .maxRetries(0)
                .build();

        // when
        CompletableFuture<EmbeddingResponse> future =
                model.embedAsync(EmbeddingRequest.builder().input("hello").build());

        assertThat(future).isNotDone();
        assertThat(httpFuture).isNotDone();

        future.cancel(true);

        // then: cancellation propagates through the aggregate (allOf) -> batch -> executeRawAsync -> HTTP client
        assertThat(httpFuture).isCancelled();
    }
}
