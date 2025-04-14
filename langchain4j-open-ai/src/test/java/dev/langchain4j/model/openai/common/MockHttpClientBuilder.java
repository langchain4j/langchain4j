package dev.langchain4j.model.openai.common;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;

import java.time.Duration;

class MockHttpClientBuilder implements HttpClientBuilder {

    private final HttpClient httpClient;

    public MockHttpClientBuilder(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

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
