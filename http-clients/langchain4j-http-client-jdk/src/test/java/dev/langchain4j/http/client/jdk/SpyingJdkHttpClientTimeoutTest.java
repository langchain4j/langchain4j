package dev.langchain4j.http.client.jdk;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SpyingHttpClient;
import java.net.ServerSocket;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;

class SpyingJdkHttpClientTimeoutTest {

    @Test
    void should_apply_read_timeout_configured_on_mock_http_client_builder() throws Exception {

        // the OS completes the TCP handshake (backlog), but nobody ever reads the request or responds
        try (ServerSocket neverRespondingServer = new ServerSocket(0)) {

            // given
            SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder());

            HttpClientBuilder httpClientBuilder = new MockHttpClientBuilder(spyingHttpClient)
                    .connectTimeout(ofSeconds(1))
                    .readTimeout(ofSeconds(1));
            HttpClient httpClient = httpClientBuilder.build();

            HttpRequest request = HttpRequest.builder()
                    .method(GET)
                    .url("http://localhost:" + neverRespondingServer.getLocalPort())
                    .build();

            // when-then
            assertThat(httpClientBuilder.connectTimeout()).isEqualTo(ofSeconds(1));
            assertThat(httpClientBuilder.readTimeout()).isEqualTo(ofSeconds(1));
            assertTimeoutPreemptively(
                    ofSeconds(30),
                    () -> assertThatThrownBy(() -> httpClient.execute(request))
                            .hasRootCauseInstanceOf(HttpTimeoutException.class));
            assertThat(spyingHttpClient.request()).isSameAs(request);
        }
    }
}
