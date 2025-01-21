package dev.langchain4j.http.jdk11;

import dev.langchain4j.http.HttpClient;
import dev.langchain4j.http.HttpClientIT;

class Jdk11HttpClientIT extends HttpClientIT {

    @Override
    protected HttpClient client() {
        return new Jdk11HttpClientBuilder()
                .build();
    }
}
