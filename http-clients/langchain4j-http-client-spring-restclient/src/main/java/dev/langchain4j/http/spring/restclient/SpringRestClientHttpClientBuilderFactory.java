package dev.langchain4j.http.spring.restclient;

import dev.langchain4j.http.HttpClientBuilder;
import dev.langchain4j.http.HttpClientBuilderFactory;

public class SpringRestClientHttpClientBuilderFactory implements HttpClientBuilderFactory {

    @Override
    public HttpClientBuilder create() {
        return new SpringRestClientHttpClientBuilder();
    }
}
