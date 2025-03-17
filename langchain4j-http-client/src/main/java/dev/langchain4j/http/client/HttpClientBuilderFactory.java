package dev.langchain4j.http.client;

import dev.langchain4j.Experimental;

@Experimental
public interface HttpClientBuilderFactory {

    HttpClientBuilder create();
}
