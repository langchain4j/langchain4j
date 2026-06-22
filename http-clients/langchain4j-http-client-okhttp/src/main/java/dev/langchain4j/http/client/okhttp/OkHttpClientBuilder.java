package dev.langchain4j.http.client.okhttp;

import dev.langchain4j.http.client.HttpClientBuilder;
import java.time.Duration;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;

public class OkHttpClientBuilder implements HttpClientBuilder {

    private static final int DEFAULT_STREAMING_BUFFER_SIZE = 16384;

    private okhttp3.OkHttpClient.Builder okHttpClientBuilder;
    private Duration connectTimeout;
    private Duration readTimeout;
    private int streamingBufferSize = DEFAULT_STREAMING_BUFFER_SIZE;

    public okhttp3.OkHttpClient.Builder okHttpClientBuilder() {
        return okHttpClientBuilder;
    }

    public OkHttpClientBuilder okHttpClientBuilder(okhttp3.OkHttpClient.Builder okHttpClientBuilder) {
        this.okHttpClientBuilder = okHttpClientBuilder;
        return this;
    }

    @Override
    public Duration connectTimeout() {
        return connectTimeout;
    }

    @Override
    public OkHttpClientBuilder connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    @Override
    public Duration readTimeout() {
        return readTimeout;
    }

    @Override
    public OkHttpClientBuilder readTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    public int streamingBufferSize() {
        return streamingBufferSize;
    }

    /**
     * Sets the size of the bounded back-pressure buffer used by the streaming ({@code executeWithPublisher})
     * path. Server-sent events are relayed to the subscriber through this buffer; if the subscriber consumes
     * slower than the server produces and the buffer overflows, the stream terminates with an error.
     * <p>
     * The default is {@code 16384}.
     *
     * @param streamingBufferSize the buffer size; must be greater than zero
     * @return the builder instance
     */
    public OkHttpClientBuilder streamingBufferSize(int streamingBufferSize) {
        this.streamingBufferSize = ensureGreaterThanZero(streamingBufferSize, "streamingBufferSize");
        return this;
    }

    @Override
    public OkHttpClient build() {
        return new OkHttpClient(this);
    }
}
