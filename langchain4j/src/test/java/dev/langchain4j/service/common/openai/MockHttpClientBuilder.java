package dev.langchain4j.service.common.openai;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;

import java.time.Duration;

public class MockHttpClientBuilder implements HttpClientBuilder {

    private HttpClient httpClient;

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
