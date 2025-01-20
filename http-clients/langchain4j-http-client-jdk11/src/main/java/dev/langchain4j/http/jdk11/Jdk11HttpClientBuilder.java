package dev.langchain4j.http.jdk11;

import dev.langchain4j.http.HttpClientBuilder;

import java.time.Duration;

public class Jdk11HttpClientBuilder implements HttpClientBuilder {

    private java.net.http.HttpClient.Builder httpClientBuilder;
    private Duration connectTimeout;
    private Duration readTimeout;

    public java.net.http.HttpClient.Builder httpClientBuilder() {
        return httpClientBuilder;
    }

    public Jdk11HttpClientBuilder httpClientBuilder(java.net.http.HttpClient.Builder httpClientBuilder) {
        this.httpClientBuilder = httpClientBuilder;
        return this;
    }

    @Override
    public Duration connectTimeout() {
        return connectTimeout;
    }

    @Override
    public Jdk11HttpClientBuilder connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    @Override
    public Duration readTimeout() {
        return readTimeout;
    }

    @Override
    public Jdk11HttpClientBuilder readTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    @Override
    public Jdk11HttpClient build() {
        return new Jdk11HttpClient(this);
    }
}
