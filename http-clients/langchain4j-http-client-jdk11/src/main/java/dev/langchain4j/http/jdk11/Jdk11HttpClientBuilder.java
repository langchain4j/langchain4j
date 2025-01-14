package dev.langchain4j.http.jdk11;

import dev.langchain4j.http.HttpClient;
import dev.langchain4j.http.HttpClientBuilder;

import java.time.Duration;

public class Jdk11HttpClientBuilder implements HttpClientBuilder {

    private java.net.http.HttpClient.Builder httpClientBuilder;
    private Duration connectTimeout; // TODO remove?
    private Duration readTimeout; // TODO remove?
    private boolean logRequests; // TODO remove?
    private boolean logResponses; // TODO remove?

    public java.net.http.HttpClient.Builder httpClientBuilder() {
        return httpClientBuilder;
    }

    public HttpClientBuilder httpClientBuilder(java.net.http.HttpClient.Builder httpClientBuilder) {
        this.httpClientBuilder = httpClientBuilder;
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
        return new Jdk11HttpClient(this);
    }
}
