package dev.langchain4j.http.spring.restclient;

import dev.langchain4j.http.HttpClient;
import dev.langchain4j.http.HttpException;
import dev.langchain4j.http.HttpMethod;
import dev.langchain4j.http.HttpRequest;
import dev.langchain4j.http.SuccessfulHttpResponse;
import dev.langchain4j.http.streaming.ServerSentEventListener;
import dev.langchain4j.http.streaming.StreamingStrategy;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.util.Map;
import java.util.function.Consumer;

import static dev.langchain4j.internal.Utils.getOrDefault;

public class SpringRestClientHttpClient implements HttpClient {

    private final RestClient delegate;
    private final AsyncTaskExecutor streamingRequestExecutor;

    public SpringRestClientHttpClient(SpringRestClientHttpClientBuilder builder) {

        RestClient.Builder restClientBuilder = getOrDefault(builder.restClientBuilder(), RestClient::builder);

        // TODO propagate this from SB starter?
        // TODO fail here and ask to set timeouts via RestClient.Builder?
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
                    .headers(responseEntity.getHeaders().toSingleValueMap())
                    .body(responseEntity.getBody())
                    .build();
        } catch (HttpStatusCodeException e) {
            throw new HttpException(e.getStatusCode().value(), e.getMessage());
        } // TODO catch other types of exceptions?
    }

    private RestClient.RequestBodySpec toSpringRestClientRequest(HttpRequest httpRequest) {
        RestClient.RequestBodySpec requestBodySpec = delegate
                .method(convert(httpRequest.method()))
                .uri(httpRequest.url())
                .headers(convert(httpRequest.headers()));

        if (httpRequest.body() != null) {
            requestBodySpec.body(httpRequest.body());
        }

        return requestBodySpec;
    }

    private static org.springframework.http.HttpMethod convert(HttpMethod httpMethod) {
        return org.springframework.http.HttpMethod.valueOf(httpMethod.name());
    }

    private static Consumer<HttpHeaders> convert(Map<String, String> headers) {
        return httpHeaders -> headers.forEach((name, value) -> {
            if (value != null) {
                httpHeaders.add(name, value);
            }
        });
    }

    @Override
    public void execute(HttpRequest httpRequest, StreamingStrategy strategy, ServerSentEventListener listener) {
        streamingRequestExecutor.execute(() -> {
            try {
                toSpringRestClientRequest(httpRequest)
                        .exchange((request, response) -> {
                            if (!response.getStatusCode().is2xxSuccessful()) {
                                // response.getStatusText() TODO
                                listener.onError(new HttpException(response.getStatusCode().value(), readBody(response)));
                                return null; // TODO
                            }

                            listener.onOpen(SuccessfulHttpResponse.builder()
                                    .statusCode(response.getStatusCode().value())
                                    .headers(response.getHeaders().toSingleValueMap())
                                    .build());

                            try (InputStream inputStream = response.getBody()) {
                                strategy.process(inputStream, listener);
                                listener.onClose();
                            } catch (Exception e) {
                                listener.onError(e); // TODO
                            }
                            return null; // TODO
                        });
            } catch (Exception e) {
                listener.onError(e);
            }
        });
    }

    private static String readBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse httpResponse) {
        return httpResponse.bodyTo(String.class);
    }
}
