package dev.langchain4j.http;

import dev.langchain4j.Experimental;

@Experimental
public interface HttpClientBuilderFactory {

    HttpClientBuilder create();
}
