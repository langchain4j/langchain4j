package dev.langchain4j.http.client.apache;

import dev.langchain4j.http.client.HttpClientBuilder;
import java.time.Duration;

public class ApacheHttpClientBuilder implements HttpClientBuilder {

    private org.apache.hc.client5.http.impl.classic.HttpClientBuilder httpClientBuilder;
    private org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder httpAsyncClientBuilder;
    private Duration connectTimeout;
    private Duration readTimeout;

    /**
     * Returns the underlying Apache {@link org.apache.hc.client5.http.impl.classic.HttpClientBuilder}.
     *
     * @return the Apache HTTP client builder, or {@code null} if not set
     */
    public org.apache.hc.client5.http.impl.classic.HttpClientBuilder httpClientBuilder() {
        return httpClientBuilder;
    }

    /**
     * Sets a pre-configured Apache {@link org.apache.hc.client5.http.impl.classic.HttpClientBuilder}
     * used to create the synchronous HTTP client.
     *
     * @param httpClientBuilder the Apache HTTP client builder
     * @return {@code this}
     */
    public ApacheHttpClientBuilder httpClientBuilder(
            org.apache.hc.client5.http.impl.classic.HttpClientBuilder httpClientBuilder) {
        this.httpClientBuilder = httpClientBuilder;
        return this;
    }

    /**
     * Returns the underlying Apache {@link org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder}.
     *
     * @return the Apache asynchronous HTTP client builder, or {@code null} if not set
     */
    public org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder httpAsyncClientBuilder() {
        return httpAsyncClientBuilder;
    }

    /**
     * Sets a pre-configured Apache {@link org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder}
     * used to create the asynchronous HTTP client (for streaming).
     *
     * @param httpAsyncClientBuilder the Apache asynchronous HTTP client builder
     * @return {@code this}
     */
    public ApacheHttpClientBuilder httpAsyncClientBuilder(
            org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder httpAsyncClientBuilder) {
        this.httpAsyncClientBuilder = httpAsyncClientBuilder;
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
    public ApacheHttpClientBuilder connectTimeout(Duration connectTimeout) {
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
    public ApacheHttpClientBuilder readTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    /**
     * Builds the {@link ApacheHttpClient}.
     *
     * @return the configured {@link ApacheHttpClient}
     */
    @Override
    public ApacheHttpClient build() {
        return new ApacheHttpClient(this);
    }
}
