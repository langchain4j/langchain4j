package dev.langchain4j.http.client;

import dev.langchain4j.Internal;
import java.time.Duration;

@Internal
public class MockHttpClientBuilder implements HttpClientBuilder {

    private final HttpClient httpClient;
    private final HttpClientBuilder delegateBuilder;

    public MockHttpClientBuilder(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.delegateBuilder = null;
    }

    /**
     * Timeouts configured on this builder (e.g. by a model) are applied to the builder of the real HTTP client
     * that the given {@link SpyingHttpClient} delegates to, if it was created from a builder.
     */
    public MockHttpClientBuilder(SpyingHttpClient spyingHttpClient) {
        this.httpClient = spyingHttpClient;
        this.delegateBuilder = spyingHttpClient.delegateBuilder();
    }

    @Override
    public Duration connectTimeout() {
        return delegateBuilder == null ? null : delegateBuilder.connectTimeout();
    }

    @Override
    public HttpClientBuilder connectTimeout(Duration timeout) {
        if (delegateBuilder != null) {
            delegateBuilder.connectTimeout(timeout);
        }
        return this;
    }

    @Override
    public Duration readTimeout() {
        return delegateBuilder == null ? null : delegateBuilder.readTimeout();
    }

    @Override
    public HttpClientBuilder readTimeout(Duration timeout) {
        if (delegateBuilder != null) {
            delegateBuilder.readTimeout(timeout);
        }
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
