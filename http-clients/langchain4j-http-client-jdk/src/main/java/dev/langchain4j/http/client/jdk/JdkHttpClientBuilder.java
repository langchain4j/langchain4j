package dev.langchain4j.http.client.jdk;

import dev.langchain4j.http.client.HttpClientBuilder;

import java.time.Duration;

public class JdkHttpClientBuilder implements HttpClientBuilder {

    private java.net.http.HttpClient.Builder httpClientBuilder;
    private Duration connectTimeout;
    private Duration readTimeout;
    private Integer streamingBufferSize;

    public java.net.http.HttpClient.Builder httpClientBuilder() {
        return httpClientBuilder;
    }

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
     */
    public JdkHttpClientBuilder streamingBufferSize(Integer streamingBufferSize) {
        this.streamingBufferSize = streamingBufferSize;
        return this;
    }

    @Override
    public JdkHttpClient build() {
        return new JdkHttpClient(this);
    }
}
