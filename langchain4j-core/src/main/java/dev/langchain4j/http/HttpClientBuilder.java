package dev.langchain4j.http;

import dev.langchain4j.Experimental;

import java.time.Duration;

@Experimental
public interface HttpClientBuilder {

    Duration connectTimeout();

    HttpClientBuilder connectTimeout(Duration timeout);

    Duration readTimeout();

    HttpClientBuilder readTimeout(Duration timeout);

    HttpClient build();
}
