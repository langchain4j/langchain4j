package dev.langchain4j.http.client.apache;

import dev.langchain4j.http.client.HttpClientBuilderFactory;

public class ApacheHttpClientBuilderFactory implements HttpClientBuilderFactory {

    @Override
    public ApacheHttpClientBuilder create() {
        return ApacheHttpClient.builder();
    }
}
