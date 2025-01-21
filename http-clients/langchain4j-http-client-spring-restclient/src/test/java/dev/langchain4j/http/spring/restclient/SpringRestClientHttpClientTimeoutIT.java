package dev.langchain4j.http.spring.restclient;

import dev.langchain4j.http.HttpClient;
import dev.langchain4j.http.HttpClientTimeoutIT;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.time.Duration;

class SpringRestClientHttpClientTimeoutIT extends HttpClientTimeoutIT {

    @Override
    protected HttpClient client(Duration readTimeout) {
        return new SpringRestClientHttpClientBuilder()
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
