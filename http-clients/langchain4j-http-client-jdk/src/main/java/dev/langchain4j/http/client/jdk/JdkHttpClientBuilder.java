package dev.langchain4j.http.client.jdk;

import dev.langchain4j.http.client.HttpClientBuilder;

import java.time.Duration;

/**
 * @deprecated as of 2.0.0, use {@link dev.langchain4j.http.client.jdk.JdkHttpClient} directly
 *             and configure timeouts on the underlying {@link java.net.http.HttpClient.Builder}.
 */
@Deprecated
public class JdkHttpClientBuilder implements HttpClientBuilder {

    private java.net.http.HttpClient.Builder httpClientBuilder;

    private Duration connectTimeout;
    private Duration readTimeout;

    public java.net.http.HttpClient.Builder httpClientBuilder() {
        return httpClientBuilder;
    }

    public JdkHttpClientBuilder httpClientBuilder(java.net.http.HttpClient.Builder httpClientBuilder) {
        this.httpClientBuilder = httpClientBuilder;
        return this;
    }

    /**
     * @deprecated timeouts should be configured on the underlying HttpClient.Builder
     */
    @Deprecated
    @Override
    public Duration connectTimeout() {
        return connectTimeout;
    }

    /**
     * @deprecated timeouts should be configured on the underlying HttpClient.Builder
     */
    @Deprecated
    @Override
    public JdkHttpClientBuilder connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    /**
     * @deprecated timeouts should be configured on the underlying HttpClient.Builder
     */
    @Deprecated
    @Override
    public Duration readTimeout() {
        return readTimeout;
    }

    /**
     * @deprecated timeouts should be configured on the underlying HttpClient.Builder
     */
    @Deprecated
    @Override
    public JdkHttpClientBuilder readTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    @Override
    public JdkHttpClient build() {
        return new JdkHttpClient(this);
    }
}
