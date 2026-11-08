package dev.langchain4j.http.client.jdk;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpClientBuilder;
import java.time.Duration;

public class JdkHttpClientBuilder implements HttpClientBuilder {

    private java.net.http.HttpClient.Builder httpClientBuilder;
    private Duration connectTimeout;
    private Duration readTimeout;
    private Integer streamingBufferSize;
    private boolean streamingBackpressure;

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
     * Cannot be combined with {@link #streamingBackpressure(boolean) streamingBackpressure(true)}, which does not
     * use this buffer.
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

    /**
     * @return whether streaming HTTP body reads follow downstream demand
     * @since 1.23.0
     */
    @Experimental
    public boolean streamingBackpressure() {
        return streamingBackpressure;
    }

    /**
     * Controls whether {@code stream(...)} propagates subscriber demand to HTTP body reads.
     * When enabled, the client drains the parsed events from the current byte batch before requesting
     * more body data. A slow subscriber pauses body consumption instead of overflowing the event buffer.
     * The parser can still retain an incomplete event spanning several byte batches.
     * <p>
     * Disabled by default to retain the bounded event-buffer behavior used by existing integrations.
     * This option applies only to the publisher streaming API, not blocking or callback-based requests.
     * Cannot be combined with {@link #streamingBufferSize(Integer)}: {@link #build()} throws an
     * {@link IllegalArgumentException} if both are set.
     *
     * @param streamingBackpressure whether to make streaming body reads follow subscriber demand
     * @return this builder
     * @since 1.23.0
     */
    @Experimental
    public JdkHttpClientBuilder streamingBackpressure(boolean streamingBackpressure) {
        this.streamingBackpressure = streamingBackpressure;
        return this;
    }

    @Override
    public JdkHttpClient build() {
        return new JdkHttpClient(this);
    }
}
