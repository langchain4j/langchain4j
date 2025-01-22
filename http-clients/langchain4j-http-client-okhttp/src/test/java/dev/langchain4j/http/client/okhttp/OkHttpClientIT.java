package dev.langchain4j.http.client.okhttp;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientIT;

class OkHttpClientIT extends HttpClientIT {

    @Override
    protected HttpClient client() {
        return new OkHttpClientBuilder()
                .build();
    }
}
