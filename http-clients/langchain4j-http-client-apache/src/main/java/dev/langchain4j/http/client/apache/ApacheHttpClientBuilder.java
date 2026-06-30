package dev.langchain4j.http.client.apache;

import dev.langchain4j.http.client.HttpClientBuilder;
import java.time.Duration;

public class ApacheHttpClientBuilder implements HttpClientBuilder {

    private org.apache.hc.client5.http.impl.classic.HttpClientBuilder httpClientBuilder;
    private org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder httpAsyncClientBuilder;
    private Duration connectTimeout;
    private Duration readTimeout;
    private Integer streamingBufferSize;

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
    public Duration connectTimeout() {
        return connectTimeout;
    }

    @Override
    public ApacheHttpClientBuilder connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    @Override
    public Duration readTimeout() {
        return readTimeout;
    }

    @Override
    public ApacheHttpClientBuilder readTimeout(Duration readTimeout) {
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
     * The default is {@value ApacheHttpClient#DEFAULT_STREAMING_BUFFER_SIZE}.
     *
     * @param streamingBufferSize the buffer size; must be greater than zero
     * @return the builder instance
     */
    public ApacheHttpClientBuilder streamingBufferSize(Integer streamingBufferSize) {
        this.streamingBufferSize = streamingBufferSize;
        return this;
    }

    @Override
    public ApacheHttpClient build() {
        return new ApacheHttpClient(this);
    }
}
