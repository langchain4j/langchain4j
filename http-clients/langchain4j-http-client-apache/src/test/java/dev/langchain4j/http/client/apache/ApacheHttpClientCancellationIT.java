package dev.langchain4j.http.client.apache;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientCancellationIT;

class ApacheHttpClientCancellationIT extends HttpClientCancellationIT {

    @Override
    protected HttpClient client() {
        return ApacheHttpClient.builder().build();
    }

    @Override
    protected boolean cancelAbortsConnection() {
        // Apache's async client buffers the whole response and does not abort an in-flight request when its
        // future is cancelled; cancellation releases the caller but the connection is not closed promptly.
        return false;
    }
}
