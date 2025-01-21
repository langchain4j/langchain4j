package dev.langchain4j.http.jdk11;

import dev.langchain4j.http.HttpClient;
import dev.langchain4j.http.HttpClientTimeoutIT;

import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletionException;

class Jdk11HttpClientTimeoutIT extends HttpClientTimeoutIT {

    @Override
    protected HttpClient client(Duration readTimeout) {
        return new Jdk11HttpClientBuilder()
                .readTimeout(readTimeout)
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedReadTimeoutExceptionTypeSync() {
        return RuntimeException.class;
    }

    @Override
    protected Class<? extends Exception> expectedReadTimeoutCauseExceptionTypeSync() {
        return HttpTimeoutException.class;
    }

    @Override
    protected Class<? extends Exception> expectedReadTimeoutExceptionTypeAsync() {
        return CompletionException.class;
    }

    @Override
    protected Class<? extends Exception> expectedReadTimeoutCauseExceptionTypeAsync() {
        return HttpTimeoutException.class;
    }
}
