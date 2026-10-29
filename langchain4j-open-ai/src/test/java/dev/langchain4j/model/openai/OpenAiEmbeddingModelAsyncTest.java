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
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    /**
     * When embedding is split across multiple batches and a non-first batch fails, the caller must see the real
     * provider error - not the {@link java.util.concurrent.CancellationException} that aborting the sibling batches
     * injects into {@code allOf}. (Without the fix, {@code allOf} surfaces batch-0's CancellationException, which
     * {@code EmbeddingModel.embedAsync} then mis-reports and whose onError listener it suppresses.)
     */
    @Test
    void multi_batch_failure_reports_the_real_error_not_a_masked_cancellation() throws Exception {
        RuntimeException realError = new RuntimeException("real batch error (e.g. rate limit)");
        CompletableFuture<SuccessfulHttpResponse> batch0 = new CompletableFuture<>(); // never completes -> aborted sibling
        AtomicInteger call = new AtomicInteger();
        HttpClient client = new HttpClient() {
            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
                // batch 0 hangs (will be aborted as a sibling); batch 1 fails with the real error
                return call.getAndIncrement() == 0 ? batch0 : CompletableFuture.failedFuture(realError);
            }
        };

        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(client))
                .modelName("text-embedding-3-small")
                .maxSegmentsPerBatch(1) // 2 inputs -> 2 batches
                .maxRetries(0)
                .build();

        CompletableFuture<EmbeddingResponse> future =
                model.embedAsync(EmbeddingRequest.builder().inputs("a", "b").build());

        // the returned future fails with the real error, not a masked CancellationException
        assertThatThrownBy(() -> future.get(5, SECONDS)).hasMessageContaining("real batch error");
    }
}
