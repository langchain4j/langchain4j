package dev.langchain4j.http.client;

import java.time.Duration;

/**
 * Builder for {@link HttpClient} instances.
 * <p>
 * Note: Timeout settings have been removed from this interface.
 * Timeouts should be configured directly on the underlying HTTP client's builder.
 * For example, with OkHttp:
 * <pre>{@code
 * OkHttpClientBuilder builder = OkHttpClient.builder();
 * builder.okHttpClientBuilder().connectTimeout(30, TimeUnit.SECONDS);
 * builder.okHttpClientBuilder().readTimeout(60, TimeUnit.SECONDS);
 * HttpClient client = builder.build();
 * }</pre>
 * <p>
 * This change simplifies the API and allows Spring's RestClient.Builder and other HTTP clients
 * that don't support per-setting overrides to work seamlessly with LangChain4j.
 *
 * @deprecated as of 2.0.0, this interface and its implementations are deprecated.
 *             Configure timeouts directly on your underlying HTTP client's builder.
 *             See the documentation for examples.
 */
@Deprecated
public interface HttpClientBuilder {

    /**
     * @deprecated timeouts should be configured on the underlying HTTP client's builder
     */
    @Deprecated
    Duration connectTimeout();


    /**
     * @deprecated timeouts should be configured on the underlying HTTP client's builder
     */
    @Deprecated
    HttpClientBuilder connectTimeout(Duration timeout);

    /**
     * @deprecated timeouts should be configured on the underlying HTTP client's builder
     */
    @Deprecated
    Duration readTimeout();


    /**
     * @deprecated timeouts should be configured on the underlying HTTP client's builder
     */
    @Deprecated
    HttpClientBuilder readTimeout(Duration timeout);

    HttpClient build();
}
