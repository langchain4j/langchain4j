package dev.langchain4j.http.client.apache;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.HttpRequest;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ApacheHttpClientAsyncFailureTest {

    @Test
    void executeAsync_should_return_a_failed_future_for_an_invalid_url() {

        HttpRequest request =
                HttpRequest.builder().method(GET).url("http://localhost/a b").build();

        assertThat(ApacheHttpClient.builder().build().executeAsync(request))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(IllegalArgumentException.class);
    }
}
