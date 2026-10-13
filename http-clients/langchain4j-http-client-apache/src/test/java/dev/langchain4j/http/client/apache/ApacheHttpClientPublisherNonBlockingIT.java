package dev.langchain4j.http.client.apache;

import dev.langchain4j.http.client.AbstractHttpClientPublisherNonBlockingIT;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.log.LoggingHttpClient;

class ApacheHttpClientPublisherNonBlockingIT extends AbstractHttpClientPublisherNonBlockingIT {

    @Override
    protected HttpClient newClient(boolean logging) {
        HttpClient client = ApacheHttpClient.builder().build();
        return logging ? new LoggingHttpClient(client, true, true) : client;
    }

    @Override
    protected String policedThreadNamePrefix() {
        // httpcore5 async reactor I/O dispatch threads (e.g. "httpclient-dispatch-1").
        return "httpclient-dispatch";
    }
}
