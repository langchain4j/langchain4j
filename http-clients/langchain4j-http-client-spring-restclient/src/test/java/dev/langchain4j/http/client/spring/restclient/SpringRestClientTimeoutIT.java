package dev.langchain4j.http.client.spring.restclient;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientTimeoutIT;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.JettyClientHttpRequestFactory;
import org.springframework.http.client.ReactorNettyClientRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

class SpringRestClientTimeoutIT extends HttpClientTimeoutIT {

    @Override
    protected List<HttpClient> clients(Duration readTimeout) {
        return List.of(
                new SpringRestClientBuilder()
                        .restClientBuilder(RestClient.builder().requestFactory(new JdkClientHttpRequestFactory()))
                        .readTimeout(readTimeout)
                        .build(),
                new SpringRestClientBuilder()
                        .restClientBuilder(RestClient.builder().requestFactory(new HttpComponentsClientHttpRequestFactory()))
                        .readTimeout(readTimeout)
                        .build(),
                new SpringRestClientBuilder()
                        .restClientBuilder(RestClient.builder().requestFactory(new JettyClientHttpRequestFactory()))
                        .readTimeout(readTimeout)
                        .build(),
                new SpringRestClientBuilder()
                        .restClientBuilder(RestClient.builder().requestFactory(new ReactorNettyClientRequestFactory()))
                        .readTimeout(readTimeout)
                        .build(),
                new SpringRestClientBuilder()
                        .restClientBuilder(RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()))
                        .readTimeout(readTimeout)
                        .build()
        );
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
