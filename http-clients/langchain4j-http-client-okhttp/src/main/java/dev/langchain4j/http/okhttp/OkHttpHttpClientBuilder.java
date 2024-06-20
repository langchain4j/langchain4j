package dev.langchain4j.http.okhttp;

import dev.langchain4j.http.HttpClient;
import dev.langchain4j.http.HttpClientBuilder;
import okhttp3.OkHttpClient;

import java.time.Duration;

public class OkHttpHttpClientBuilder implements HttpClientBuilder {

    private OkHttpClient.Builder okHttpClientBuilder;
    private Duration connectTimeout;
    private Duration readTimeout;
    private boolean logRequests;
    private boolean logResponses;

    public OkHttpClient.Builder okHttpClientBuilder() {
        return okHttpClientBuilder;
    }

    public HttpClientBuilder okHttpClientBuilder(OkHttpClient.Builder okHttpClientBuilder) {
        this.okHttpClientBuilder = okHttpClientBuilder;
        return this;
    }

    @Override
    public Duration connectTimeout() {
        return connectTimeout;
    }

    @Override
    public HttpClientBuilder connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    @Override
    public Duration readTimeout() {
        return readTimeout;
    }

    @Override
    public HttpClientBuilder readTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    @Override
    public boolean logRequests() {
        return logRequests;
    }

    @Override
    public HttpClientBuilder logRequests(boolean logRequests) {
        this.logRequests = logRequests;
        return this;
    }

    @Override
    public boolean logResponses() {
        return logResponses;
    }

    @Override
    public HttpClientBuilder logResponses(boolean logResponses) {
        this.logResponses = logResponses;
        return this;
    }

    @Override
    public HttpClient build() {
        return new OkHttpHttpClient(this);
    }
}
