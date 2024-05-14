package dev.langchain4j.http;

import dev.langchain4j.Experimental;

import java.time.Duration;

@Experimental
public interface HttpClientBuilder {  // TODO move inside HttpClient?

    Duration timeout();

    HttpClientBuilder timeout(Duration timeout);

    boolean logRequests();

    HttpClientBuilder logRequests(boolean logRequests);

    boolean logResponses();

    HttpClientBuilder logResponses(boolean logResponses);

    // TODO customHeaders / defaultHeaders?
    // TODO proxy
    // TODO retries?
    // TODO what else?

    HttpClient build();
}
