package dev.langchain4j.http.client.jdk;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpException;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.streaming.ServerSentEventListener;
import dev.langchain4j.http.client.streaming.StreamingStrategy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.getOrDefault;

// TODO review
public class JdkHttpClient implements HttpClient {

    private final java.net.http.HttpClient delegate;
    private final Duration readTimeout;

    public JdkHttpClient(JdkHttpClientBuilder builder) {
        java.net.http.HttpClient.Builder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder(), java.net.http.HttpClient::newBuilder);
        if (builder.connectTimeout() != null) {
            httpClientBuilder.connectTimeout(builder.connectTimeout());
        }
        this.delegate = httpClientBuilder.build();
        this.readTimeout = builder.readTimeout();
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) throws HttpException {
        try {
            java.net.http.HttpRequest httpRequest = toJdkHttpRequest(request);

            java.net.http.HttpResponse<String> response = delegate.send(httpRequest, BodyHandlers.ofString());

            if (!isSuccessful(response)) {
                throw new HttpException(response.statusCode(), response.body());
            }

            return fromJdkHttpResponse(response, response.body());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private java.net.http.HttpRequest toJdkHttpRequest(HttpRequest request) {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(request.url()));

        request.headers().forEach((name, value) -> {
            if (value != null) {
                builder.header(name, value);
            }
        });

        BodyPublisher bodyPublisher;
        if (request.body() != null) {
            bodyPublisher = BodyPublishers.ofString(request.body());
        } else {
            bodyPublisher = BodyPublishers.noBody();
        }
        builder.method(request.method().name(), bodyPublisher);

        if (readTimeout != null) {
            builder.timeout(readTimeout);
        }

        return builder.build();
    }

    private static SuccessfulHttpResponse fromJdkHttpResponse(java.net.http.HttpResponse<?> response, String body) {
        Map<String, String> headers = new LinkedHashMap<>();
        response.headers().map().forEach((name, values) -> headers.put(name, String.join(", ", values)));

        return SuccessfulHttpResponse.builder()
                .statusCode(response.statusCode())
                .headers(headers)
                .body(body)
                .build();
    }

    @Override
    public void execute(HttpRequest request, StreamingStrategy strategy, ServerSentEventListener listener) {
        java.net.http.HttpRequest httpRequest = toJdkHttpRequest(request);

        delegate.sendAsync(httpRequest, BodyHandlers.ofInputStream())
                .thenAccept(response -> {
                    try {
                        if (!isSuccessful(response)) {
                            listener.onError(new HttpException(response.statusCode(), readBody(response)));
                            return;
                        }

                        // TODO how to handle exceptions thrown from listener?
                        // TODO in all clients
                        // TODO test

                        listener.onOpen(fromJdkHttpResponse(response, null));

                        try (InputStream inputStream = response.body()) {
                            strategy.process(inputStream, listener);
                            listener.onClose();
                        }
                    } catch (Exception e) {
                        listener.onError(e);
                    }
                })
                .exceptionally(throwable -> {
                    listener.onError(throwable);
                    return null;
                });
    }

    private static String readBody(java.net.http.HttpResponse<InputStream> response) {
        try (InputStream inputStream = response.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "Cannot read error response body: " + e.getMessage();
        }
    }

    private static boolean isSuccessful(java.net.http.HttpResponse<?> httpResponse) {
        int statusCode = httpResponse.statusCode();
        return statusCode >= 200 && statusCode < 300;
    }
}
