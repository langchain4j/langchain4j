package dev.langchain4j.http.jdk11;

import dev.langchain4j.http.HttpClientBuilderFactory;

public class Jdk11HttpClientBuilderFactory implements HttpClientBuilderFactory {

    @Override
    public Jdk11HttpClientBuilder create() {
        return new Jdk11HttpClientBuilder();
    }
}
