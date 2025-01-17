package dev.langchain4j.http.okhttp;

import dev.langchain4j.http.HttpClientBuilderFactory;

public class OkHttpHttpClientBuilderFactory implements HttpClientBuilderFactory {

    @Override
    public OkHttpHttpClientBuilder create() {
        return new OkHttpHttpClientBuilder();
    }
}
