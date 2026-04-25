package dev.langchain4j.http.client.jdk;

import dev.langchain4j.http.client.HttpClientBuilder;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @deprecated as of 2.0. Timeout configuration should be performed on the underlying
 * {@code HttpClient.Builder} directly. This class now stores the timeout values only
 * for backward compatibility and logs a deprecation warning when used.
 */
@Deprecated(forRemoval = true)
public class JdkHttpClientBuilder implements HttpClientBuilder {

    private static final Logger logger = Logger.getLogger(JdkHttpClientBuilder.class.getName());

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

    @Override
    @Deprecated(forRemoval = true)
    public Duration connectTimeout() {
        return connectTimeout;
    }

    @Override
    @Deprecated(forRemoval = true)
    public JdkHttpClientBuilder connectTimeout(Duration connectTimeout) {
        logger.warning("[DEPRECATION] HttpClientBuilder.connectTimeout(Duration) is deprecated. Set timeout on the underlying HttpClient.Builder.");
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
    public JdkHttpClientBuilder readTimeout(Duration readTimeout) {
        logger.warning("[DEPRECATION] HttpClientBuilder.readTimeout(Duration) is deprecated. Set timeout on the underlying HttpClient.Builder.");
        this.readTimeout = readTimeout;
        return this;
    }

    @Override
    public JdkHttpClient build() {
        // Apply stored timeouts to the underlying builder for backward compatibility
        if (httpClientBuilder == null) {
            httpClientBuilder = java.net.http.HttpClient.newBuilder();
        }
        if (connectTimeout != null) {
            httpClientBuilder.connectTimeout(connectTimeout);
        }
        return new JdkHttpClient(this);
    }
}
