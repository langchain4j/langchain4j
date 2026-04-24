package dev.langchain4j.http.client.jdk;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientTimeoutIT;

import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;

class JdkHttpClientTimeoutIT extends HttpClientTimeoutIT {

    @Override
    protected List<HttpClient> clients(Duration readTimeout) {
        return List.of(
                // Using deprecated builder method
                JdkHttpClient.builder()
                        .readTimeout(readTimeout)
                        .build(),
                // Using underlying HTTP client builder directly (recommended way)
                JdkHttpClient.builder()
                        .httpClientBuilder(java.net.http.HttpClient.newBuilder()
                                .connectTimeout(java.time.Duration.ofMillis(readTimeout.toMillis())))
                        .build());
    }

    @Override
    protected Class<? extends Exception> expectedReadTimeoutRootCauseExceptionType() {
        return HttpTimeoutException.class;
    }
}
