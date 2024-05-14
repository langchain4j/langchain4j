package dev.langchain4j.http.okhttp;

import dev.langchain4j.http.HttpClient;
import dev.langchain4j.http.HttpClientBuilder;
import okhttp3.OkHttpClient;

import java.time.Duration;

public class OkHttpHttpClientBuilder implements HttpClientBuilder {

    private OkHttpClient.Builder okHttpClientBuilder;
    private Duration timeout;
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
    public Duration timeout() {
        return timeout;
    }

    @Override
    public HttpClientBuilder timeout(Duration timeout) {
        this.timeout = timeout;
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
