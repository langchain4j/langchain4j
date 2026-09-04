package dev.langchain4j.http.client.apache;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientPublisherIT;

import java.util.List;

class ApacheHttpClientPublisherIT extends HttpClientPublisherIT {

    @Override
    protected List<HttpClient> clients() {
        return List.of(ApacheHttpClient.builder().build());
    }
}
