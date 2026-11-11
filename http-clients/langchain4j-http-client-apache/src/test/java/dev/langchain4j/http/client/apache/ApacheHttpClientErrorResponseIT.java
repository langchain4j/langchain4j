package dev.langchain4j.http.client.apache;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientErrorResponseIT;

class ApacheHttpClientErrorResponseIT extends HttpClientErrorResponseIT {

    @Override
    protected HttpClient client() {
        return ApacheHttpClient.builder().build();
    }

    @Override
    protected boolean cancelAbortsConnection() {
        // Same reactor limitation as in ApacheHttpClientCancellationIT: cancelling stops the delivery of
        // signals, but the connection stays open until the server ends the response or a timeout fires.
        // Failing the response consumer's result callback on cancel does not close it either.
        return false;
    }
}
