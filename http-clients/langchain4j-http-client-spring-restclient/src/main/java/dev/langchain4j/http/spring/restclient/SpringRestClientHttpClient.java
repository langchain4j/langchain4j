package dev.langchain4j.http.spring.restclient;

import dev.langchain4j.http.AbstractHttpClient;
import dev.langchain4j.http.HttpException;
import dev.langchain4j.http.HttpMethod;
import dev.langchain4j.http.HttpRequest;
import dev.langchain4j.http.HttpResponse;
import dev.langchain4j.http.ServerSentEvent;
import dev.langchain4j.http.ServerSentEventListener;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.function.Consumer;

import static dev.langchain4j.internal.Utils.getOrDefault;

public class SpringRestClientHttpClient extends AbstractHttpClient {

    private final RestClient delegate;
    private final TaskExecutor taskExecutor; // TODO better name? streamingTaskExecutor?
    private final boolean logRequests;
    private final boolean logResponses;

    public SpringRestClientHttpClient(SpringRestClientHttpClientBuilder builder) {
        RestClient.Builder restClientBuilder = getOrDefault(builder.restClientBuilder(), RestClient::builder);

        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(builder.connectTimeout())
                .withReadTimeout(builder.readTimeout());
        ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactories.get(settings);

        this.delegate = restClientBuilder
                .requestFactory(clientHttpRequestFactory)
                .build();

        this.taskExecutor = getOrDefault(builder.taskExecutor(), () -> {
            if (builder.createDefaultTaskExecutor()) {
                return createDefaultTaskExecutor();
            } else {
                return null;
            }
        });

        this.logRequests = builder.logRequests();
        this.logResponses = builder.logResponses();
    }

    private static TaskExecutor createDefaultTaskExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        // TODO set meaningful defaults
        taskExecutor.initialize();
        return taskExecutor;
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
        RestClient.RequestBodySpec requestBodySpec = delegate
                .method(convert(httpRequest.method()))
                .uri(httpRequest.url())
                .headers(convert(httpRequest.headers()));

        if (httpRequest.body() != null) {
            requestBodySpec
                    // TODO content type/length?
                    .body(httpRequest.body());
        }

        return requestBodySpec;
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

        // TODO reuse SSE parsing logic

        Map<String, String> headers = httpRequest.headers();
        if (headers.containsKey("Content-Type") && "application/x-ndjson".equals(headers.get("Content-Type"))) {
            // TODO extract into a separate HttpClient method? provide as a strategy?
            taskExecutor.execute(() -> handleNdJson(httpRequest, listener));
        } else {
            taskExecutor.execute(() -> handleServerSentEvents(httpRequest, listener));
        }
    }

    private void handleServerSentEvents(HttpRequest httpRequest, ServerSentEventListener listener) {
        toSpringRestClientRequest(httpRequest)
                .exchange((request, response) -> {

                    if (!response.getStatusCode().is2xxSuccessful()) {
                        // response.getStatusText() TODO
                        listener.onError(new HttpException(response.getStatusCode().value(), readBody(response)));
                        return null; // TODO
                    }

                    listener.onStart(HttpResponse.builder()
                            .statusCode(response.getStatusCode().value())
                            .headers(response.getHeaders().toSingleValueMap()) // TODO test
                            .build());

                    try (InputStream inputStream = response.getBody();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

                        String event = null;
                        while (true) {
                            String line = reader.readLine();
                            if (line == null) {
                                break;
                            } else if (line.startsWith("event:")) {
                                event = line.substring("event:".length()).trim();
                            } else if (line.startsWith("data:")) {
                                String data = line.substring("data:".length()).trim();
                                ServerSentEvent serverSentEvent = new ServerSentEvent(event, data);
                                listener.onEvent(serverSentEvent);
                                event = null;
                            }
                        }

                        listener.onFinish();
                    } catch (IOException e) {
                        listener.onError(e); // TODO
                    }

                    return null; // TODO
                });
    }

    private void handleNdJson(HttpRequest httpRequest, ServerSentEventListener listener) {
        toSpringRestClientRequest(httpRequest)
                .exchange((request, response) -> {

                    if (!response.getStatusCode().is2xxSuccessful()) {
                        // response.getStatusText() TODO
                        listener.onError(new HttpException(response.getStatusCode().value(), readBody(response)));
                        return null; // TODO
                    }

                    listener.onStart(HttpResponse.builder()
                            .statusCode(response.getStatusCode().value())
                            .headers(response.getHeaders().toSingleValueMap()) // TODO test
                            .build());

                    try (InputStream inputStream = response.getBody();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

                        String line;
                        while ((line = reader.readLine()) != null) {
                            listener.onEvent(new ServerSentEvent(null, line));
                        }

                        listener.onFinish(); // TODO?
                    } catch (IOException e) { // TODO?
                        listener.onError(e);
                    }

                    return null; // TODO
                });
    }

    private static String readBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse httpResponse) {
        return httpResponse.bodyTo(String.class);
//        try (InputStream body = httpResponse.bodyTo(String.)) {
//            return new String(body.readAllBytes()); // TODO charset?
//        } catch (IOException e) {
//            return "[cannot read error response body]: " + e.getMessage(); // TODO?
//        }
    }

    @Override
    protected boolean logRequests() {
        return logRequests;
    }

    @Override
    public boolean logResponses() {
        return logResponses;
    }

    @Override
    public void close() {
        // TODO
//        taskExecutor.close(); // TODO?
    }
}
