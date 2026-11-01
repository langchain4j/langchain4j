package dev.langchain4j.http.client.jdk;

import dev.langchain4j.http.client.AbstractHttpClientPublisherNonBlockingIT;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.log.LoggingHttpClient;

class JdkHttpClientPublisherNonBlockingIT extends AbstractHttpClientPublisherNonBlockingIT {

    @Override
    protected HttpClient newClient(boolean logging) {
        HttpClient client = JdkHttpClient.builder().build();
        return logging ? new LoggingHttpClient(client, true, true) : client;
    }

    @Override
    protected String policedThreadNamePrefix() {
        // The JDK HttpClient parses and dispatches the response body on its worker threads ("HttpClient-*").
        return "HttpClient-";
    }
}
