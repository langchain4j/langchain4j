package dev.langchain4j.http.client.okhttp;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientPublisherIT;

import java.util.List;

class OkHttpClientPublisherIT extends HttpClientPublisherIT {

    @Override
    protected List<HttpClient> clients() {
        return List.of(OkHttpClient.builder().build());
    }
}
