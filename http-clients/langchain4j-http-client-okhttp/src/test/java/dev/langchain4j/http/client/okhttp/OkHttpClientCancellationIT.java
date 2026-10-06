package dev.langchain4j.http.client.okhttp;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientCancellationIT;

class OkHttpClientCancellationIT extends HttpClientCancellationIT {

    @Override
    protected HttpClient client() {
        return OkHttpClient.builder().build();
    }
}
