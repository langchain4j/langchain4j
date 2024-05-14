package dev.langchain4j.http.spring.restclient;

import dev.langchain4j.http.HttpClient;
import dev.langchain4j.http.HttpClientBuilder;
import org.springframework.web.client.RestClient;

import java.time.Duration;

public class SpringRestClientHttpClientBuilder implements HttpClientBuilder {

    private RestClient.Builder restClientBuilder;
    private Duration timeout;
    private boolean logRequests;
    private boolean logResponses;

    public RestClient.Builder restClientBuilder() {
        return restClientBuilder;
    }

    public HttpClientBuilder restClientBuilder(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
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
        return new SpringRestClientHttpClient(this);
    }
}
