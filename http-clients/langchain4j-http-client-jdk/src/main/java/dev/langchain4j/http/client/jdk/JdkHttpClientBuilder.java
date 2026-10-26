package dev.langchain4j.http.client.jdk;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpClientBuilder;
import java.time.Duration;

public class JdkHttpClientBuilder implements HttpClientBuilder {

    private java.net.http.HttpClient httpClient;
    private java.net.http.HttpClient.Builder httpClientBuilder;
    private Duration connectTimeout;
    private Duration readTimeout;
    private Integer streamingBufferSize;

    /**
     * Returns the supplied JDK HTTP client.
     *
     * @return the supplied JDK HTTP client, or {@code null} when one will be created during {@link #build()}
     * @since 1.23.0
     */
    public java.net.http.HttpClient httpClient() {
        return httpClient;
    }

    /**
     * Sets the JDK {@link java.net.http.HttpClient} to use as the underlying client.
     * <p>
     * This allows multiple {@link JdkHttpClient} instances to share the connection pool and other resources owned by
     * the supplied client, while retaining their own read timeout and streaming buffer size.
     * <p>
     * When set, {@link #httpClientBuilder(java.net.http.HttpClient.Builder)} and
     * {@link #connectTimeout(Duration)} are ignored because an already-built JDK HTTP client cannot be reconfigured.
     * The supplied client is used as-is and is not modified or closed by {@link JdkHttpClient}.
     * Its configured HTTP version is respected for all requests; callers using plain {@code http://} URLs can build
     * it with {@code .version(java.net.http.HttpClient.Version.HTTP_1_1)} to avoid a JDK-default {@code Upgrade: h2c}
     * request to servers that do not support it.
     *
     * @param httpClient the JDK HTTP client, or {@code null} to create one when {@link #build()} is called
     * @return this builder
     * @since 1.23.0
     */
    public JdkHttpClientBuilder httpClient(java.net.http.HttpClient httpClient) {
        this.httpClient = httpClient;
        return this;
    }

    public java.net.http.HttpClient.Builder httpClientBuilder() {
        return httpClientBuilder;
    }

    /**
     * Sets the JDK {@link java.net.http.HttpClient.Builder} used to create the underlying client.
     * <p>
     * When not set, a default client is used: plain {@code http://} requests are sent over HTTP/1.1,
     * and {@code https://} requests use HTTP/2 when the server supports it.
     * <p>
     * When set, the HTTP version configured on the given builder is used for all requests.
     * If no version is configured, the JDK default is HTTP/2, which for plain {@code http://} URLs
     * sends an {@code Upgrade: h2c} request that many servers do not handle correctly
     * (for example, they ignore the request body).
     * Call {@code .version(java.net.http.HttpClient.Version.HTTP_1_1)} on the builder to avoid this.
     *
     * @param httpClientBuilder the JDK HTTP client builder
     * @return this builder
     */
    public JdkHttpClientBuilder httpClientBuilder(java.net.http.HttpClient.Builder httpClientBuilder) {
        this.httpClientBuilder = httpClientBuilder;
        return this;
    }

    @Override
    public Duration connectTimeout() {
        return connectTimeout;
    }

    @Override
    public JdkHttpClientBuilder connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    @Override
    public Duration readTimeout() {
        return readTimeout;
    }

    @Override
    public JdkHttpClientBuilder readTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    /**
     * @return the bounded back-pressure buffer size used by {@code stream(...)}, or {@code null} for the default.
     * @since 1.20.0
     */
    @Experimental
    public Integer streamingBufferSize() {
        return streamingBufferSize;
    }

    /**
     * Sets the size of the bounded back-pressure buffer used by the streaming ({@code stream})
     * path. Server-sent events are relayed to the subscriber through this buffer; if the subscriber consumes
     * slower than the server produces and the buffer overflows, the stream terminates with an error.
     * <p>
     * The default is {@value JdkHttpClient#DEFAULT_STREAMING_BUFFER_SIZE}.
     *
     * @param streamingBufferSize the buffer size; must be greater than zero
     * @return the builder instance
     * @since 1.20.0
     */
    @Experimental
    public JdkHttpClientBuilder streamingBufferSize(Integer streamingBufferSize) {
        this.streamingBufferSize = streamingBufferSize;
        return this;
    }

    @Override
    public JdkHttpClient build() {
        return new JdkHttpClient(this);
    }
}
