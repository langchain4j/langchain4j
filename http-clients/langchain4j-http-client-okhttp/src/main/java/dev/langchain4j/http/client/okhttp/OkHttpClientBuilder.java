package dev.langchain4j.http.client.okhttp;

import dev.langchain4j.http.client.HttpClientBuilder;
import java.time.Duration;

/**
 * @deprecated as of 2.0.0, use {@link dev.langchain4j.http.client.okhttp.OkHttpClient} directly
 *             and configure timeouts on the underlying {@link okhttp3.OkHttpClient.Builder}.
 */
@Deprecated
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

    /**
     * @deprecated timeouts should be configured on the underlying OkHttpClient.Builder
     */
    @Deprecated
    @Override
    public Duration connectTimeout() {
        return connectTimeout;
    }

    /**
     * @deprecated timeouts should be configured on the underlying OkHttpClient.Builder
     */
    @Deprecated
    @Override
    public OkHttpClientBuilder connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    /**
     * @deprecated timeouts should be configured on the underlying OkHttpClient.Builder
     */
    @Deprecated
    @Override
    public Duration readTimeout() {
        return readTimeout;
    }

    /**
     * @deprecated timeouts should be configured on the underlying OkHttpClient.Builder
     */
    @Deprecated
    @Override
    public OkHttpClientBuilder readTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    @Override
    public OkHttpClient build() {
        return new OkHttpClient(this);
    }
}
