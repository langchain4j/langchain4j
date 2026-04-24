package dev.langchain4j.http.client.apache;

import dev.langchain4j.http.client.HttpClientBuilder;
import java.time.Duration;

/**
 * @deprecated as of 2.0.0, use {@link dev.langchain4j.http.client.apache.ApacheHttpClient} directly
 *             and configure timeouts on the underlying Apache HttpClient builders.
 */
@Deprecated
public class ApacheHttpClientBuilder implements HttpClientBuilder {

    private org.apache.hc.client5.http.impl.classic.HttpClientBuilder httpClientBuilder;
    private org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder httpAsyncClientBuilder;

    private Duration connectTimeout;
    private Duration readTimeout;

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

    /**
     * @deprecated timeouts should be configured on the underlying Apache HttpClient builders
     */
    @Deprecated
    @Override
    public Duration connectTimeout() {
        return connectTimeout;
    }


    /**
     * @deprecated timeouts should be configured on the underlying Apache HttpClient builders
     */
    @Deprecated
    @Override
    public ApacheHttpClientBuilder connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    /**
     * @deprecated timeouts should be configured on the underlying Apache HttpClient builders
     */
    @Deprecated
    @Override
    public Duration readTimeout() {
        return readTimeout;
    }

    /**
     * @deprecated timeouts should be configured on the underlying Apache HttpClient builders
     */
    @Deprecated
    @Override
    public ApacheHttpClientBuilder readTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    @Override
    public ApacheHttpClient build() {
        return new ApacheHttpClient(this);
    }
}
