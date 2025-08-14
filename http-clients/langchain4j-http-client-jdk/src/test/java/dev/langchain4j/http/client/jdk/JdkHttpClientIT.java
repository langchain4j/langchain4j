package dev.langchain4j.http.client.jdk;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientIT;

import java.util.List;

class JdkHttpClientIT extends HttpClientIT {

    @Override
    protected List<HttpClient> clients() {
        return List.of(
                JdkHttpClient.builder().build()
        );
    }
}
