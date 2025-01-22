package dev.langchain4j.http.client.okhttp;

import dev.langchain4j.http.client.HttpClientBuilderFactory;

public class OkHttpClientBuilderFactory implements HttpClientBuilderFactory {

    @Override
    public OkHttpClientBuilder create() {
        return new OkHttpClientBuilder();
    }
}
