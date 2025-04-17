package dev.langchain4j.http.client;

import java.time.Duration;

public interface HttpClientBuilder {

    Duration connectTimeout();

    HttpClientBuilder connectTimeout(Duration timeout);

    Duration readTimeout();

    HttpClientBuilder readTimeout(Duration timeout);

    HttpClient build();
}
