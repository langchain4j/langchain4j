package dev.langchain4j.http.spring.restclient;

import dev.langchain4j.http.HttpClient;
import dev.langchain4j.http.HttpClientIT;

class SpringRestClientHttpClientIT extends HttpClientIT {

    @Override
    protected HttpClient client() {
        return new SpringRestClientHttpClientBuilder()
                .build();
    }

    @Override
    protected boolean assertExceptionMessage() {
        return false; // TODO
    }
}
