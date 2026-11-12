package dev.langchain4j.http.client.jdk;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.HttpRequest;
import java.time.Duration;
import org.junit.jupiter.api.Test;

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
}
