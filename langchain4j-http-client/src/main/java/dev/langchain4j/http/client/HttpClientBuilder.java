package dev.langchain4j.http.client;

import java.time.Duration;

/**
 * Builder for {@link HttpClient} instances.
 * <p>
 * Note: {@code connectTimeout} and {@code readTimeout} settings on this builder
 * are deprecated as of 2.0. Timeouts should be configured directly on the
 * underlying HTTP client. Some HTTP clients (e.g. Spring's {@code RestClient.Builder})
 * do not support customizing timeouts without overriding other properties.
 * <p>
 * To set timeouts, configure them on your underlying HTTP client:
 * <ul>
 *   <li>OkHttp: {@code new OkHttpClient.Builder().connectTimeout(...).readTimeout(...)}</li>
 *   <li>Apache HttpClient: configure via {@code RequestConfig}</li>
 *   <li>JDK HttpClient: {@code HttpClient.newBuilder().connectTimeout(...).timeout(...)}</li>
 * </ul>
 */
public interface HttpClientBuilder {

    /**
     * @deprecated as of 2.0. Configure connect timeout on the underlying HTTP client instead.
     */
    @Deprecated(forRemoval = true)
    Duration connectTimeout();

    /**
     * @deprecated as of 2.0. Configure connect timeout on the underlying HTTP client instead.
     */
    @Deprecated(forRemoval = true)
    HttpClientBuilder connectTimeout(Duration timeout);

    /**
     * @deprecated as of 2.0. Configure read timeout on the underlying HTTP client instead.
     */
    @Deprecated(forRemoval = true)
    Duration readTimeout();

    /**
     * @deprecated as of 2.0. Configure read timeout on the underlying HTTP client instead.
     */
    @Deprecated(forRemoval = true)
    HttpClientBuilder readTimeout(Duration timeout);

    HttpClient build();
}
