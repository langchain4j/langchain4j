package dev.langchain4j.http.client.okhttp;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class OkHttpClientAsyncFailureTest {

    @Test
    void executeAsync_should_return_a_failed_future_for_a_url_without_scheme() {

        HttpRequest request = HttpRequest.builder().method(GET).url("localhost:1/test").build();

        assertThat(OkHttpClient.builder().build().executeAsync(request))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executeAsync_should_return_a_failed_future_for_an_invalid_header_value() {

        HttpRequest request = HttpRequest.builder()
                .method(GET)
                .url("http://localhost:1/test")
                .addHeader("Authorization", "Bearer key\n")
                .build();

        assertThat(OkHttpClient.builder().build().executeAsync(request))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stream_should_signal_a_url_without_scheme_to_the_subscriber_instead_of_throwing() {

        HttpRequest request = HttpRequest.builder().method(GET).url("localhost:1/test").build();
        CompletableFuture<Throwable> error = new CompletableFuture<>();

        OkHttpClient.builder().build().stream(request).subscribe(new Flow.Subscriber<HttpStreamingEvent>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(HttpStreamingEvent event) {}

            @Override
            public void onError(Throwable throwable) {
                error.complete(throwable);
            }

            @Override
            public void onComplete() {
                error.completeExceptionally(new AssertionError("the stream completed"));
            }
        });

        assertThat(error).succeedsWithin(Duration.ofSeconds(5)).isInstanceOf(IllegalArgumentException.class);
    }
}
