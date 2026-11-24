package dev.langchain4j.http.client.jdk;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientCancellationIT;

class JdkHttpClientCancellationIT extends HttpClientCancellationIT {

    @Override
    protected HttpClient client() {
        return JdkHttpClient.builder().build();
    }
}
