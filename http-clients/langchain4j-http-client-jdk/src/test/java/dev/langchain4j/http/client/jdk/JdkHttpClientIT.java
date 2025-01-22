package dev.langchain4j.http.client.jdk;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientIT;

class JdkHttpClientIT extends HttpClientIT {

    @Override
    protected HttpClient client() {
        return new JdkHttpClientBuilder()
                .build();
    }
}
