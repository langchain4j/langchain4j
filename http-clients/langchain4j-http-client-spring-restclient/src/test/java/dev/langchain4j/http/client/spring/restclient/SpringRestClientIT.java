package dev.langchain4j.http.client.spring.restclient;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientIT;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.JettyClientHttpRequestFactory;
import org.springframework.http.client.ReactorNettyClientRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.List;

class SpringRestClientIT extends HttpClientIT {

    @Override
    protected List<HttpClient> clients() {
        return List.of(
                new SpringRestClientBuilder()
                        .restClientBuilder(RestClient.builder().requestFactory(new JdkClientHttpRequestFactory()))
                        .build(),
                new SpringRestClientBuilder()
                        .restClientBuilder(RestClient.builder().requestFactory(new HttpComponentsClientHttpRequestFactory()))
                        .build(),
                new SpringRestClientBuilder()
                        .restClientBuilder(RestClient.builder().requestFactory(new JettyClientHttpRequestFactory()))
                        .build(),
                new SpringRestClientBuilder()
                        .restClientBuilder(RestClient.builder().requestFactory(new ReactorNettyClientRequestFactory()))
                        .build(),
                new SpringRestClientBuilder()
                        .restClientBuilder(RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()))
                        .build()
        );
    }
}
