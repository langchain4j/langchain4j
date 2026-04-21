package dev.langchain4j.http.client.apache;

import dev.langchain4j.http.client.HttpClientBuilder;
import java.time.Duration;

/**
 * @deprecated as of 2.0. Timeout configuration should be performed on the underlying
 * Apache {@code HttpClientBuilder} directly. This class now stores the timeout values
 * only for backward compatibility and logs a deprecation warning when used.
 */
@Deprecated(forRemoval = true)
public class ApacheHttpClientBuilder implements HttpClientBuilder {

    private org.apache.hc.client5.http.impl.classic.HttpClientBuilder httpClientBuilder;
    private org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder httpAsyncClientBuilder;
    private Duration connectTimeout;
    private Duration readTimeout;

    public org.apache.hc.client5.http.impl.classic.HttpClientBuilder httpClientBuilder() {
        return httpClientBuilder;
    }

    public ApacheHttpClientBuilder httpClientBuilder(
            org.apache.hc.client5.http.impl.classic.HttpClientBuilder httpClientBuilder) {
        this.httpClientBuilder = httpClientBuilder;
        return this;
    }

    public org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder httpAsyncClientBuilder() {
        return httpAsyncClientBuilder;
    }

    public ApacheHttpClientBuilder httpAsyncClientBuilder(
            org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder httpAsyncClientBuilder) {
        this.httpAsyncClientBuilder = httpAsyncClientBuilder;
        return this;
    }

    @Override
    @Deprecated(forRemoval = true)
    public Duration connectTimeout() {
        return connectTimeout;
    }

    @Override
    @Deprecated(forRemoval = true)
    public ApacheHttpClientBuilder connectTimeout(Duration connectTimeout) {
        System.err.println("[DEPRECATION] HttpClientBuilder.connectTimeout(Duration) is deprecated. Set timeout on the underlying HttpClientBuilder.");
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
    public ApacheHttpClientBuilder readTimeout(Duration readTimeout) {
        System.err.println("[DEPRECATION] HttpClientBuilder.readTimeout(Duration) is deprecated. Set timeout on the underlying HttpClientBuilder.");
        this.readTimeout = readTimeout;
        return this;
    }

    @Override
    public ApacheHttpClient build() {
        // Apply stored timeouts to the underlying builder for backward compatibility
        if (connectTimeout != null && httpClientBuilder != null) {
            httpClientBuilder.setConnectionTimeToLive(connectTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        if (readTimeout != null && httpClientBuilder != null) {
            // Apache HttpClient does not have a direct read timeout; we set socket timeout
            httpClientBuilder.setDefaultSocketConfig(org.apache.hc.client5.http.config.SocketConfig.custom()
                    .setSoTimeout(readTimeout)
                    .build());
        }
        return new ApacheHttpClient(this);
    }
}
