package dev.langchain4j.http.client;

import dev.langchain4j.Internal;
import java.time.Duration;

@Internal
public class MockHttpClientBuilder implements HttpClientBuilder {

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

    public static class MockClientFactory implements HttpClientBuilderFactory {

        public static MockClientFactory of() {
            return new MockClientFactory();
        }

        @Override
        public HttpClientBuilder create() {
            return new MockHttpClientBuilder(new MockHttpClient());
        }
    }
}
