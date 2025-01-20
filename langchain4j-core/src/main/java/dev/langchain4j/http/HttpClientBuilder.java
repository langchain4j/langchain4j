package dev.langchain4j.http;

import dev.langchain4j.Experimental;

import java.time.Duration;

@Experimental
public interface HttpClientBuilder {

    // TODO baseUrl?
    // TODO customHeaders / defaultHeaders?
    // TODO Proxy/ProxySelector/user/pwd? - postpone till OpenAI migration

    Duration connectTimeout();

    HttpClientBuilder connectTimeout(Duration timeout);

    Duration readTimeout();

    HttpClientBuilder readTimeout(Duration timeout);

    HttpClient build();
}
