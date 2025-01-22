package dev.langchain4j.http.client.spring.restclient;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpException;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.InputStream;

import static dev.langchain4j.internal.Utils.getOrDefault;

public class SpringRestClient implements HttpClient {

    private final RestClient delegate;
    private final AsyncTaskExecutor streamingRequestExecutor;

    public SpringRestClient(SpringRestClientBuilder builder) {

        RestClient.Builder restClientBuilder = getOrDefault(builder.restClientBuilder(), RestClient::builder);

        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS;
        if (builder.connectTimeout() != null) {
            settings = settings.withConnectTimeout(builder.connectTimeout());
        }
        if (builder.readTimeout() != null) {
            settings = settings.withReadTimeout(builder.readTimeout());
        }
        ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactories.get(settings);

        this.delegate = restClientBuilder
                .requestFactory(clientHttpRequestFactory)
                .build();

        this.streamingRequestExecutor = getOrDefault(builder.streamingRequestExecutor(), () -> {
            if (builder.createDefaultStreamingRequestExecutor()) {
                return createDefaultStreamingRequestExecutor();
            } else {
                return null;
            }
        });
    }

    private static AsyncTaskExecutor createDefaultStreamingRequestExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        // TODO set meaningful defaults
        taskExecutor.initialize();
        return taskExecutor;
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest httpRequest) throws HttpException {
        try {
            ResponseEntity<String> responseEntity = toSpringRestClientRequest(httpRequest)
                    .retrieve()
                    .toEntity(String.class);

            return SuccessfulHttpResponse.builder()
                    .statusCode(responseEntity.getStatusCode().value())
                    .headers(responseEntity.getHeaders())
                    .body(responseEntity.getBody())
                    .build();
        } catch (RestClientResponseException e) {
            throw new HttpException(e.getStatusCode().value(), e.getMessage());
        }
    }

    private RestClient.RequestBodySpec toSpringRestClientRequest(HttpRequest httpRequest) {
        RestClient.RequestBodySpec requestBodySpec = delegate
                .method(convert(httpRequest.method()))
                .uri(httpRequest.url())
                .headers(httpHeaders -> httpHeaders.putAll(httpRequest.headers()));

        if (httpRequest.body() != null) {
            requestBodySpec.body(httpRequest.body());
        }

        return requestBodySpec;
    }

    private static org.springframework.http.HttpMethod convert(HttpMethod httpMethod) {
        return org.springframework.http.HttpMethod.valueOf(httpMethod.name());
    }

    @Override
    public void execute(HttpRequest httpRequest, ServerSentEventParser parser, ServerSentEventListener listener) {
        streamingRequestExecutor.execute(() -> {
            try {
                toSpringRestClientRequest(httpRequest)
                        .exchange((request, response) -> {

                            int statusCode = response.getStatusCode().value();

                            if (!response.getStatusCode().is2xxSuccessful()) {
                                String body = response.bodyTo(String.class);
                                listener.onError(new HttpException(statusCode, body));
                                return null;
                            }

                            listener.onOpen(SuccessfulHttpResponse.builder()
                                    .statusCode(statusCode)
                                    .headers(response.getHeaders())
                                    .build());

                            try (InputStream inputStream = response.getBody()) {
                                parser.parse(inputStream, listener);
                                listener.onClose();
                            } catch (Exception e) {
                                listener.onError(e);
                            }

                            return null;
                        });
            } catch (Exception e) {
                listener.onError(e);
            }
        });
    }
}
