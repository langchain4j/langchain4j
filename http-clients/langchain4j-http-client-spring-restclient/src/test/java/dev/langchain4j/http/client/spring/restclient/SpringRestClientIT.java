package dev.langchain4j.http.client.spring.restclient;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientIT;

class SpringRestClientIT extends HttpClientIT {

    @Override
    protected HttpClient client() {
        return new SpringRestClientBuilder()
                .build();
    }

    @Override
    protected boolean assertExceptionMessage() {
        return false; // TODO
    }
}
