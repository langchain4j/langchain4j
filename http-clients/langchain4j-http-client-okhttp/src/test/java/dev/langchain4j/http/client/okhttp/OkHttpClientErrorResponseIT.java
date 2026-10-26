package dev.langchain4j.http.client.okhttp;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientErrorResponseIT;

class OkHttpClientErrorResponseIT extends HttpClientErrorResponseIT {

    @Override
    protected HttpClient client() {
        return OkHttpClient.builder().build();
    }
}
