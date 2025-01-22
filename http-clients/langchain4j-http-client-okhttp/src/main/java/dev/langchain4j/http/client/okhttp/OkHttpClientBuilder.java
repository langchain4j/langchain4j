package dev.langchain4j.http.client.okhttp;

import dev.langchain4j.http.client.HttpClientBuilder;

import java.time.Duration;

public class OkHttpClientBuilder implements HttpClientBuilder {

    private okhttp3.OkHttpClient.Builder okHttpClientBuilder;
    private Duration connectTimeout;
    private Duration readTimeout;

    public okhttp3.OkHttpClient.Builder okHttpClientBuilder() {
        return okHttpClientBuilder;
    }

    public OkHttpClientBuilder okHttpClientBuilder(okhttp3.OkHttpClient.Builder okHttpClientBuilder) {
        this.okHttpClientBuilder = okHttpClientBuilder;
        return this;
    }

    @Override
    public Duration connectTimeout() {
        return connectTimeout;
    }

    @Override
    public OkHttpClientBuilder connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    @Override
    public Duration readTimeout() {
        return readTimeout;
    }

    @Override
    public OkHttpClientBuilder readTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    @Override
    public OkHttpClient build() {
        return new OkHttpClient(this);
    }
}
