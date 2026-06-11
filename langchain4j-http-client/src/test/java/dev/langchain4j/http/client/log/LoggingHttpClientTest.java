package dev.langchain4j.http.client.log;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoggingHttpClientTest {

    private static final HttpRequest REQUEST =
            HttpRequest.builder().method(GET).url("http://localhost/x").build();

    // TODO add missing tests

    @Test
    void executeAsync_delegates_to_wrapped_client_and_passes_response_through() throws Exception {
        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of())
                .body("hi")
                .build();
        MockHttpClient delegate = MockHttpClient.thatAlwaysResponds(response);
        LoggingHttpClient client = new LoggingHttpClient(delegate, true, true);

        SuccessfulHttpResponse result = client.executeAsync(REQUEST).get(5, SECONDS);

        assertThat(result).isSameAs(response);
        assertThat(delegate.request()).isSameAs(REQUEST);
    }

    @Test
    void executeAsync_propagates_failure_from_wrapped_client() {
        RuntimeException failure = new RuntimeException("boom");
        HttpClient delegate = new HttpClient() {
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
                return CompletableFuture.failedFuture(failure);
            }
        };
        LoggingHttpClient client = new LoggingHttpClient(delegate, true, true);

        CompletableFuture<SuccessfulHttpResponse> future = client.executeAsync(REQUEST);

        assertThatThrownBy(() -> future.get(5, SECONDS)).hasCause(failure);
    }
}
