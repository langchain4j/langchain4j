package dev.langchain4j.http.client.jdk;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientPublisherIT;

import java.util.List;

class JdkHttpClientPublisherIT extends HttpClientPublisherIT {

    @Override
    protected List<HttpClient> clients() {
        return List.of(JdkHttpClient.builder().build());
    }
}
