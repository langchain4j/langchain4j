package dev.langchain4j.http.client.okhttp;

import dev.langchain4j.http.client.HttpClientBuilder;
import java.time.Duration;

public class OkHttpClientBuilder implements HttpClientBuilder {

    private okhttp3.OkHttpClient.Builder okHttpClientBuilder;
    private Duration connectTimeout;
    private Duration readTimeout;

    /**
     * Returns the underlying {@link okhttp3.OkHttpClient.Builder}.
     *
     * @return the OkHttp client builder, or {@code null} if not set
     */
    public okhttp3.OkHttpClient.Builder okHttpClientBuilder() {
        return okHttpClientBuilder;
    }

    /**
     * Sets a pre-configured {@link okhttp3.OkHttpClient.Builder} used to create the HTTP client.
     *
     * @param okHttpClientBuilder the OkHttp client builder
     * @return {@code this}
     */
    public OkHttpClientBuilder okHttpClientBuilder(okhttp3.OkHttpClient.Builder okHttpClientBuilder) {
        this.okHttpClientBuilder = okHttpClientBuilder;
        return this;
    }

    /**
     * Returns the connect timeout.
     *
     * @return the connect timeout, or {@code null} if not set
     */
    @Override
    public Duration connectTimeout() {
        return connectTimeout;
    }

    /**
     * Sets the maximum time to wait when establishing a connection.
     *
     * @param connectTimeout the connect timeout
     * @return {@code this}
     */
    @Override
    public OkHttpClientBuilder connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    /**
     * Returns the read timeout.
     *
     * @return the read timeout, or {@code null} if not set
     */
    @Override
    public Duration readTimeout() {
        return readTimeout;
    }

    /**
     * Sets the maximum time to wait for data when reading a response.
     *
     * @param readTimeout the read timeout
     * @return {@code this}
     */
    @Override
    public OkHttpClientBuilder readTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    /**
     * Builds the {@link OkHttpClient}.
     *
     * @return the configured {@link OkHttpClient}
     */
    @Override
    public OkHttpClient build() {
        return new OkHttpClient(this);
    }
}
