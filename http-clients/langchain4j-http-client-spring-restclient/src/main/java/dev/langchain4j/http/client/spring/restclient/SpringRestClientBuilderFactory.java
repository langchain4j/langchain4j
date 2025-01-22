package dev.langchain4j.http.client.spring.restclient;

import dev.langchain4j.http.client.HttpClientBuilderFactory;

public class SpringRestClientBuilderFactory implements HttpClientBuilderFactory {

    @Override
    public SpringRestClientBuilder create() {
        return new SpringRestClientBuilder();
    }
}
