package dev.langchain4j.model.openai;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Deterministic coverage of the non-blocking retry on {@link OpenAiEmbeddingModel#doEmbedAsync}: a mock HTTP client
 * drives retriable / non-retriable failures so the async embedding path is shown to retry (and map) exactly like the
 * blocking path, without hitting a real server.
 */
class OpenAiEmbeddingModelAsyncRetryTest {

    // language=json
    private static final String SUCCESS_BODY =
            """
            {
              "object": "list",
              "data": [{"object": "embedding", "index": 0, "embedding": [0.1, 0.2, 0.3]}],
              "model": "text-embedding-3-small",
              "usage": {"prompt_tokens": 1, "total_tokens": 1}
            }
            """;

    private static HttpClient httpClient(Supplier<CompletableFuture<SuccessfulHttpResponse>> responder) {
        return new HttpClient() {
            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
                return responder.get();
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static SuccessfulHttpResponse okResponse() {
        return SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("content-type", List.of("application/json")))
                .body(SUCCESS_BODY)
                .build();
    }

    private static EmbeddingModel model(HttpClient httpClient, int maxRetries) {
        return OpenAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .modelName("text-embedding-3-small")
                .apiKey("test-key")
                .maxRetries(maxRetries)
                .build();
    }

    private static EmbeddingRequest request() {
        return EmbeddingRequest.builder().input("hello").build();
    }

    @Test
    void embedAsync_retries_a_retriable_failure_and_then_succeeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        EmbeddingModel model = model(
                httpClient(() -> calls.incrementAndGet() <= 2
                        ? CompletableFuture.failedFuture(new HttpException(429, "rate limited"))
                        : CompletableFuture.completedFuture(okResponse())),
                2);

        EmbeddingResponse response = model.embedAsync(request()).get(30, SECONDS);

        assertThat(response.embeddings()).hasSize(1);
        assertThat(calls).as("two retriable (429) failures + one success").hasValue(3);
    }

    @Test
    void embedAsync_does_not_retry_a_non_retriable_failure() {
        AtomicInteger calls = new AtomicInteger();
        EmbeddingModel model = model(
                httpClient(() -> {
                    calls.incrementAndGet();
                    return CompletableFuture.failedFuture(new HttpException(401, "unauthorized"));
                }),
                5);

        // a 401 maps to AuthenticationException (NonRetriableException) - it must fail immediately, no retries
        assertThatThrownBy(() -> model.embedAsync(request()).get(10, SECONDS))
                .hasCauseInstanceOf(AuthenticationException.class);
        assertThat(calls).hasValue(1);
    }
}
