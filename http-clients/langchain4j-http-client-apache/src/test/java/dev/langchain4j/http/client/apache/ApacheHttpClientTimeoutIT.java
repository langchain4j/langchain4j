package dev.langchain4j.http.client.apache;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientTimeoutIT;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

class ApacheHttpClientTimeoutIT extends HttpClientTimeoutIT {

    @Override
    protected List<HttpClient> clients(Duration readTimeout) {
        return List.of(ApacheHttpClient.builder().readTimeout(readTimeout).build());
    }

    @Override
    protected Class<? extends Exception> expectedReadTimeoutRootCauseExceptionType() {
        return SocketTimeoutException.class;
    }

    @Override
    protected int readTimeoutMillis() {
        return 1000;
    }

    @Override
    protected String[] readAsyncMessageKeywords() {
        return new String[] {"1000", "MILLISECONDS"};
    }

    @Override
    protected String[] readSyncMessageKeywords() {
        return new String[] {"Read", "timed", "out"};
    }
}
