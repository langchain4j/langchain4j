package dev.langchain4j.http.client.okhttp;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.HttpRequest;
import java.time.Duration;
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
}
