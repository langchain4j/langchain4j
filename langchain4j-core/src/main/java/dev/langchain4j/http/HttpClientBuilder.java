package dev.langchain4j.http;

import dev.langchain4j.Experimental;

import java.time.Duration;

@Experimental
public interface HttpClientBuilder {  // TODO move inside HttpClient?

    Duration connectTimeout();

    HttpClientBuilder connectTimeout(Duration timeout);

    Duration readTimeout();

    HttpClientBuilder readTimeout(Duration timeout);

    boolean logRequests();

    HttpClientBuilder logRequests(boolean logRequests);

    boolean logResponses();

    HttpClientBuilder logResponses(boolean logResponses);

    // TODO baseUrl?
    // TODO retries?
    // TODO customHeaders / defaultHeaders?
    // TODO proxy?
    // TODO what else?

    HttpClient build();
}
