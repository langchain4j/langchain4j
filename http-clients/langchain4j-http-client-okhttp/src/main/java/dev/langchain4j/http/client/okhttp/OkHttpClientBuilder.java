package dev.langchain4j.http.client.okhttp;

import dev.langchain4j.http.client.HttpClientBuilder;
import java.time.Duration;

/**
 * @deprecated as of 2.0. Timeout configuration should be performed on the underlying
 * OkHttp {@code OkHttpClient.Builder} directly. This class now stores the timeout values
 * only for backward compatibility and logs a deprecation warning when used.
 */
@Deprecated(forRemoval = true)
public class OkHttpClientBuilder implements HttpClientBuilder {

    private okhttp3.OkHttpClient.Builder okHttpClientBuilder;
    private Duration connectTimeout;
    private Duration readTimeout;

    public okhttp3.OkHttpClient.Builder okHttpClientBuilder() {
        return okHttpClientBuilder;
    }

    public OkHttpClientBuilder okHttpClientBuilder(okhttp3.OkHttpClient.Builder okHttpClientBuilder) {
        this.okHttpClientBuilder = okHttpClientBuilder;
        return this;
    }

    @Override
    @Deprecated(forRemoval = true)
    public Duration connectTimeout() {
        return connectTimeout;
    }

    @Override
    @Deprecated(forRemoval = true)
    public OkHttpClientBuilder connectTimeout(Duration connectTimeout) {
        System.err.println("[DEPRECATION] HttpClientBuilder.connectTimeout(Duration) is deprecated. Set timeout on the underlying OkHttpClient.Builder.");
        this.connectTimeout = connectTimeout;
        return this;
    }

    @Override
    @Deprecated(forRemoval = true)
    public Duration readTimeout() {
        return readTimeout;
    }

    @Override
    @Deprecated(forRemoval = true)
    public OkHttpClientBuilder readTimeout(Duration readTimeout) {
        System.err.println("[DEPRECATION] HttpClientBuilder.readTimeout(Duration) is deprecated. Set timeout on the underlying OkHttpClient.Builder.");
        this.readTimeout = readTimeout;
        return this;
    }

    @Override
    public OkHttpClient build() {
        // Apply stored timeouts to the underlying builder for backward compatibility
        if (okHttpClientBuilder == null) {
            okHttpClientBuilder = new okhttp3.OkHttpClient.Builder();
        }
        if (connectTimeout != null) {
            okHttpClientBuilder.connectTimeout(connectTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        if (readTimeout != null) {
            okHttpClientBuilder.readTimeout(readTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        return new OkHttpClient(this);
    }
}
