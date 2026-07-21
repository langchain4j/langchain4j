package dev.langchain4j.model.cohere;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Deterministic coverage of the non-blocking retry on {@link CohereScoringModel#scoreAllAsync}: a mock HTTP client
 * drives retriable / non-retriable failures so the async rerank path is shown to retry (and map) exactly like the
 * blocking path, without hitting a real server.
 */
class CohereScoringModelAsyncRetryTest {

    // language=json
    private static final String SUCCESS_BODY =
            """
            {"results":[{"index":0,"relevance_score":0.9}],"meta":{"billed_units":{"search_units":1}}}
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

    private static ScoringModel model(HttpClient httpClient, int maxRetries) {
        return CohereScoringModel.builder()
                .httpClientBuilder(new FixedHttpClientBuilder(httpClient))
                .baseUrl("http://localhost")
                .apiKey("test-key")
                .modelName("rerank-english-v3.0")
                .maxRetries(maxRetries)
                .build();
    }

    /** Minimal {@link HttpClientBuilder} that always builds the given (mock) {@link HttpClient}. */
    private record FixedHttpClientBuilder(HttpClient httpClient) implements HttpClientBuilder {
        @Override
        public Duration connectTimeout() {
            return null;
        }

        @Override
        public HttpClientBuilder connectTimeout(Duration timeout) {
            return this;
        }

        @Override
        public Duration readTimeout() {
            return null;
        }

        @Override
        public HttpClientBuilder readTimeout(Duration timeout) {
            return this;
        }

        @Override
        public HttpClient build() {
            return httpClient;
        }
    }

    private static List<TextSegment> segments() {
        return List.of(TextSegment.from("labrador retriever"));
    }

    @Test
    void scoreAllAsync_retries_a_retriable_failure_and_then_succeeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ScoringModel model = model(
                httpClient(() -> calls.incrementAndGet() <= 2
                        ? CompletableFuture.failedFuture(new HttpException(429, "rate limited"))
                        : CompletableFuture.completedFuture(okResponse())),
                2);

        Response<List<Double>> response =
                model.scoreAllAsync(segments(), "tell me about dogs").get(30, SECONDS);

        assertThat(response.content()).hasSize(1);
        assertThat(calls).as("two retriable (429) failures + one success").hasValue(3);
    }

    @Test
    void scoreAllAsync_does_not_retry_a_non_retriable_failure() {
        AtomicInteger calls = new AtomicInteger();
        ScoringModel model = model(
                httpClient(() -> {
                    calls.incrementAndGet();
                    return CompletableFuture.failedFuture(new HttpException(401, "unauthorized"));
                }),
                5);

        // a 401 maps to AuthenticationException (NonRetriableException) - it must fail immediately, no retries
        assertThatThrownBy(() -> model.scoreAllAsync(segments(), "q").get(10, SECONDS))
                .hasCauseInstanceOf(AuthenticationException.class);
        assertThat(calls).hasValue(1);
    }
}
