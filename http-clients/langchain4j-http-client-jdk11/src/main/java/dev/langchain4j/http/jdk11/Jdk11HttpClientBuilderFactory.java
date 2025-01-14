package dev.langchain4j.http.jdk11;

import dev.langchain4j.http.HttpClientBuilder;
import dev.langchain4j.http.HttpClientBuilderFactory;

public class Jdk11HttpClientBuilderFactory implements HttpClientBuilderFactory {

    @Override
    public HttpClientBuilder create() {
        return new Jdk11HttpClientBuilder();
    }
}
