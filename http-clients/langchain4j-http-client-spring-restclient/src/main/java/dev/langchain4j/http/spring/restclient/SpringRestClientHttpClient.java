package dev.langchain4j.http.spring.restclient;

import dev.langchain4j.http.*;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.function.Consumer;

import static dev.langchain4j.internal.Utils.getOrDefault;

public class SpringRestClientHttpClient extends AbstractHttpClient {

    private final RestClient restClient;
    private final boolean logRequests;
    private final boolean logResponses;

    public SpringRestClientHttpClient(SpringRestClientHttpClientBuilder builder) {
        RestClient.Builder restClientBuilder = getOrDefault(builder.restClientBuilder(), RestClient::builder);

        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(builder.connectTimeout())
                .withReadTimeout(builder.readTimeout());
        ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactories.get(settings);

        this.restClient = restClientBuilder
                .requestFactory(clientHttpRequestFactory)
                .build();
        this.logRequests = builder.logRequests();
        this.logResponses = builder.logResponses();
    }

    @Override
    protected HttpResponse doExecute(HttpRequest httpRequest) {

        ResponseEntity<String> responseEntity = toSpringRestClientRequest(httpRequest)
                .retrieve()
                .toEntity(String.class);

        return HttpResponse.builder()
                .statusCode(responseEntity.getStatusCode().value()) // TODO test
                .headers(responseEntity.getHeaders().toSingleValueMap()) // TODO test
                .body(responseEntity.getBody())
                .build();
    }

    private RestClient.RequestBodySpec toSpringRestClientRequest(HttpRequest httpRequest) {
        return restClient
                .method(convert(httpRequest.method()))
                .uri(httpRequest.url())
                .headers(convert(httpRequest.headers()))
                // TODO content type/length?
                .body(httpRequest.body());
    }

    private static org.springframework.http.HttpMethod convert(HttpMethod httpMethod) {
        switch (httpMethod) {
            case GET:
                return org.springframework.http.HttpMethod.GET;
            case POST:
                return org.springframework.http.HttpMethod.POST;
            default:
                throw new RuntimeException("Unsupported HTTP method: " + httpMethod);
        }
    }

    private static Consumer<HttpHeaders> convert(Map<String, String> headers) {
        return httpHeaders -> headers.forEach((name, value) -> {
            if (value != null) {
                httpHeaders.add(name, value);
            }
        });
    }

    @Override
    protected void doExecute(HttpRequest httpRequest, ServerSentEventListener listener) {

        toSpringRestClientRequest(httpRequest)
                .exchange((request, response) -> {
                    listener.onStart(HttpResponse.builder()
                            .statusCode(response.getStatusCode().value())
                            .headers(response.getHeaders().toSingleValueMap()) // TODO test
                            .body(null) // TODO test
                            .build());
                    try (InputStream inputStream = response.getBody()) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

                            String event = null;

                            while (true) {
                                String line = reader.readLine();
                                if (line == null) {
                                    break;
                                } else if (line.startsWith("event:")) {
                                    event = line.substring("event:".length()).trim();
                                } else if (line.startsWith("data:")) {
                                    String data = line.substring("data:".length()).trim();
                                    ServerSentEvent serverSentEvent = ServerSentEvent.builder()
                                            .type(event)
                                            .data(data)
                                            .build();
                                    listener.onEvent(serverSentEvent);
                                    event = null;
                                }
                            }
                        }
                    } catch (Exception e) {
                        listener.onError(e); // TODO
                    }

                    return null; // TODO
                });
    }

    @Override
    protected boolean logRequests() {
        return logRequests;
    }

    @Override
    public boolean logResponses() {
        return logResponses;
    }
}