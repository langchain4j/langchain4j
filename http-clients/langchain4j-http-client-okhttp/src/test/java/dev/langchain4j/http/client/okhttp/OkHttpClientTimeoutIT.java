package dev.langchain4j.http.client.okhttp;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientTimeoutIT;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OkHttpClientTimeoutIT extends HttpClientTimeoutIT {

    @Override
    protected List<HttpClient> clients(Duration readTimeout) {
        return List.of(
                // Using deprecated builder method
                OkHttpClient.builder().readTimeout(readTimeout).build(),
                // Using underlying HTTP client builder directly (recommended way)
                OkHttpClient.builder()
                        .okHttpClientBuilder()
                        .readTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS)
                        .build());
    }

    @Override
    protected void assertCause(Throwable throwable) {
        assertThat(throwable).hasCauseExactlyInstanceOf(java.net.SocketTimeoutException.class);
    }

    @Override
    protected Class<? extends Exception> expectedReadTimeoutRootCauseExceptionType() {
        return null;
    }
}
