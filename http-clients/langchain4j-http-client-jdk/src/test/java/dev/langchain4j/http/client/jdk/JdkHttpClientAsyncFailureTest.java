package dev.langchain4j.http.client.jdk;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

class JdkHttpClientAsyncFailureTest {

    @Test
    void executeAsync_should_return_a_failed_future_for_an_invalid_url() {

        HttpRequest request =
                HttpRequest.builder().method(GET).url("http://localhost/a b").build();

        assertThat(JdkHttpClient.builder().build().executeAsync(request))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executeAsync_should_return_a_failed_future_for_a_restricted_header() {

        HttpRequest request = HttpRequest.builder()
                .method(GET)
                .url("http://localhost:1/test")
                .addHeader("Host", "example.com")
                .build();

        assertThat(JdkHttpClient.builder().build().executeAsync(request))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stream_should_signal_an_invalid_url_to_the_subscriber_instead_of_throwing() {

        HttpRequest request = HttpRequest.builder().method(GET).url("http://localhost/a b").build();
        CompletableFuture<Throwable> error = new CompletableFuture<>();

        JdkHttpClient.builder().build().stream(request).subscribe(new Flow.Subscriber<HttpStreamingEvent>() {

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

    @Test
    void execute_should_report_an_invalid_url_to_the_listener_instead_of_throwing() {

        HttpRequest request = HttpRequest.builder().method(GET).url("http://localhost/a b").build();
        CompletableFuture<Throwable> error = new CompletableFuture<>();
        ServerSentEventListener listener = error::complete;

        assertThatCode(() -> JdkHttpClient.builder().build().execute(request, new DefaultServerSentEventParser(), listener))
                .doesNotThrowAnyException();

        assertThat(error).succeedsWithin(Duration.ofSeconds(5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void execute_should_throw_what_the_listener_throws_for_an_invalid_url() {
        // a streaming model passes the failure on to its handler, whose onError may throw on the calling thread

        HttpRequest request = HttpRequest.builder().method(GET).url("http://localhost/a b").build();
        RuntimeException onErrorFailure = new IllegalStateException("onError failed");
        ServerSentEventListener listener = error -> {
            throw onErrorFailure;
        };

        assertThatThrownBy(() -> JdkHttpClient.builder().build().execute(request, new DefaultServerSentEventParser(), listener))
                .isSameAs(onErrorFailure);
    }

    @Test
    void stream_should_signal_a_null_parser_to_the_subscriber_instead_of_throwing() {

        HttpRequest request = HttpRequest.builder().method(GET).url("http://localhost:1/test").build();

        for (boolean backpressure : new boolean[] {false, true}) {
            List<String> signals = new CopyOnWriteArrayList<>();
            JdkHttpClient client = JdkHttpClient.builder().streamingBackpressure(backpressure).build();

            assertThatCode(() -> client.stream(request, null).subscribe(new Flow.Subscriber<HttpStreamingEvent>() {

                        @Override
                        public void onSubscribe(Flow.Subscription subscription) {
                            signals.add("onSubscribe");
                        }

                        @Override
                        public void onNext(HttpStreamingEvent event) {
                            signals.add("onNext");
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            signals.add("onError: " + throwable.getClass().getSimpleName());
                        }

                        @Override
                        public void onComplete() {
                            signals.add("onComplete");
                        }
                    }))
                    .doesNotThrowAnyException();

            assertThat(signals).containsExactly("onSubscribe", "onError: IllegalArgumentException");
        }
    }
}
