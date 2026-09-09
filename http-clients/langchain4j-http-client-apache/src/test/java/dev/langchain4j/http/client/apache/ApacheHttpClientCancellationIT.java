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
        // Apache's async client does not abort an in-flight request when its future is cancelled: the caller is
        // released (the future completes with CancellationException), but the reactor keeps the pending connection
        // open rather than closing it promptly. This is independent of response buffering - it holds even with the
        // non-buffering streaming consumer used by stream() (verified: switching executeAsync to the lower-level
        // producer/consumer API does not change it), so it is a property of the async reactor, not the Simple API.
        return false;
    }
}
