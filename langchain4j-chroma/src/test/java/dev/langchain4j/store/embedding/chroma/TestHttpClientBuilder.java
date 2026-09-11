package dev.langchain4j.store.embedding.chroma;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import java.time.Duration;

/**
 * An {@link HttpClientBuilder} that always builds the given {@link HttpClient} and ignores the timeouts.
 */
record TestHttpClientBuilder(HttpClient httpClient) implements HttpClientBuilder {

    @Override
    public Duration connectTimeout() {
        return null;
    }

    @Override
    public HttpClientBuilder connectTimeout(Duration timeout) {
        return this;
    }

    @Override
    public Duration readTimeout() {
        return null;
    }

    @Override
    public HttpClientBuilder readTimeout(Duration timeout) {
        return this;
    }

    @Override
    public HttpClient build() {
        return httpClient;
    }
}
