package dev.langchain4j.http.client.spring.restclient;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientTimeoutIT;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.time.Duration;

class SpringRestClientTimeoutIT extends HttpClientTimeoutIT {

    @Override
    protected HttpClient client(Duration readTimeout) {
        return new SpringRestClientBuilder()
                .readTimeout(readTimeout)
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedReadTimeoutExceptionTypeSync() {
        return ResourceAccessException.class;
    }

    @Override
    protected Class<? extends Exception> expectedReadTimeoutCauseExceptionTypeSync() {
        return SocketTimeoutException.class;
    }

    @Override
    protected Class<? extends Exception> expectedReadTimeoutExceptionTypeAsync() {
        return ResourceAccessException.class;
    }

    @Override
    protected Class<? extends Exception> expectedReadTimeoutCauseExceptionTypeAsync() {
        return SocketTimeoutException.class;
    }
}
