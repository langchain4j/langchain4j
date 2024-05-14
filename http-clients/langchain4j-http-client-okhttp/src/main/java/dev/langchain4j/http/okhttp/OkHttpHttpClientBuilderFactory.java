package dev.langchain4j.http.okhttp;

import dev.langchain4j.http.HttpClientBuilder;
import dev.langchain4j.http.HttpClientBuilderFactory;

public class OkHttpHttpClientBuilderFactory implements HttpClientBuilderFactory {

    @Override
    public HttpClientBuilder create() {
        return new OkHttpHttpClientBuilder();
    }
}
