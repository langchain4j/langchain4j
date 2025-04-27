package dev.langchain4j.http.client.jdk;

import dev.langchain4j.http.client.HttpClientBuilderFactory;

public class JdkHttpClientBuilderFactory implements HttpClientBuilderFactory {

    @Override
    public JdkHttpClientBuilder create() {
        return JdkHttpClient.builder();
    }
}
