package dev.langchain4j.http.okhttp;

import dev.langchain4j.http.HttpClient;
import dev.langchain4j.http.HttpClientTimeoutIT;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;

class OkHttpHttpClientTimeoutIT extends HttpClientTimeoutIT {

    @Override
    protected HttpClient client(Duration readTimeout) {
        return new OkHttpHttpClientBuilder()
                .readTimeout(readTimeout)
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedReadTimeoutExceptionTypeSync() {
        return RuntimeException.class;
    }

    @Override
    protected Class<? extends Exception> expectedReadTimeoutCauseExceptionTypeSync() {
        return SocketTimeoutException.class;
    }

    @Override
    protected Class<? extends Exception> expectedReadTimeoutExceptionTypeAsync() {
        return SocketTimeoutException.class;
    }

    @Override
    protected Class<? extends Exception> expectedReadTimeoutCauseExceptionTypeAsync() {
        return SocketException.class;
    }
}
