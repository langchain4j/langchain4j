package dev.langchain4j.http.client.jdk;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientErrorResponseIT;

class JdkHttpClientBackpressureErrorResponseIT extends HttpClientErrorResponseIT {

    @Override
    protected HttpClient client() {
        return JdkHttpClient.builder().streamingBackpressure(true).build();
    }
}
