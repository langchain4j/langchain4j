package dev.langchain4j.http.jdk11;

import dev.langchain4j.http.HttpClient;
import dev.langchain4j.http.HttpException;
import dev.langchain4j.http.HttpRequest;
import dev.langchain4j.http.HttpResponse;
import dev.langchain4j.http.ServerSentEvent;
import dev.langchain4j.http.ServerSentEventListener;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.langchain4j.internal.Utils.getOrDefault;

// TODO review
public class Jdk11HttpClient implements HttpClient {

    private final java.net.http.HttpClient delegate;
    private final Duration readTimeout; // TODO concern

    public Jdk11HttpClient(Jdk11HttpClientBuilder builder) {
        java.net.http.HttpClient.Builder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder(), java.net.http.HttpClient::newBuilder);
        if (builder.connectTimeout() != null) {
            httpClientBuilder.connectTimeout(builder.connectTimeout());
        }
        this.delegate = httpClientBuilder.build();
        this.readTimeout = builder.readTimeout();
    }

    @Override
    public HttpResponse execute(HttpRequest request) {
        try {
            java.net.http.HttpRequest httpRequest = toJdkHttpRequest(request);

            java.net.http.HttpResponse<String> response = delegate.send(httpRequest, BodyHandlers.ofString());

            if (!isSuccessful(response)) {
                throw new HttpException(response.statusCode(), response.body());
            }

            return fromJdk11HttpResponse(response, response.body());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to execute request", e); // TODO
        }
    }

    private static HttpResponse fromJdk11HttpResponse(java.net.http.HttpResponse<?> httpResponse, String body) {
        Map<String, String> headers = new HashMap<>();
        httpResponse.headers().map().forEach((name, values) -> headers.put(name, String.join(", ", values)));

        return HttpResponse.builder()
                .statusCode(httpResponse.statusCode())
                .headers(headers)
                .body(body)
                .build();
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventListener listener) {
        java.net.http.HttpRequest httpRequest = toJdkHttpRequest(request);

        if (request.headers().containsKey("Content-Type")
                && "application/x-ndjson".equals(request.headers().get("Content-Type"))) {
            // TODO do not rely on it
            handleNdJson(httpRequest, listener);
        } else {
            handleServerSentEvents(httpRequest, listener);
        }
    }

    private void handleNdJson(java.net.http.HttpRequest request, ServerSentEventListener listener) {
        delegate.sendAsync(request, BodyHandlers.ofLines())
                .thenAccept(response -> {

                    if (!isSuccessful(response)) {
                        listener.onError(new HttpException(response.statusCode(), readBody(response)));
                        return;
                    }

                    // TODO how to handle exceptions thrown from listener?

                    listener.onStart(fromJdk11HttpResponse(response, null));

                    response.body().forEach(line ->
                            listener.onEvent(new ServerSentEvent(null, line))
                    );

                    listener.onFinish();
                })
                .exceptionally(throwable -> {
                    listener.onError(throwable);
                    return null;
                });
    }

    private void handleServerSentEvents(java.net.http.HttpRequest request, ServerSentEventListener listener) {
        delegate.sendAsync(request, BodyHandlers.ofLines())
                .thenAccept(response -> {

                    if (!isSuccessful(response)) {
                        listener.onError(new HttpException(response.statusCode(), readBody(response)));
                        return;
                    }

                    // TODO how to handle exceptions thrown from listener?

                    listener.onStart(fromJdk11HttpResponse(response, null));

                    try {
                        AtomicReference<String> eventReference = new AtomicReference<>();
                        response.body().forEach(line -> {
                            if (line.startsWith("event:")) {
                                // TODO drop event after double \n
                                eventReference.set(line.substring("event:".length()).trim());
                            } else if (line.startsWith("data:")) {
                                String data = line.substring("data:".length()).trim();
                                String event = eventReference.get();
                                listener.onEvent(new ServerSentEvent(event, data));
                                eventReference.set(null);
                            } else {
                                // TODO ignore?
                            }
                        });

                        listener.onFinish();

                    } catch (Exception e) {
                        listener.onError(e);
                    }
                })
                .exceptionally(throwable -> {
                    listener.onError(throwable);
                    return null;
                });
    }

    private static String readBody(java.net.http.HttpResponse<Stream<String>> response) {
        return response.body()
                .collect(Collectors.joining("\n")); // TODO delimiter
    }

    private java.net.http.HttpRequest toJdkHttpRequest(HttpRequest request) {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(request.url()));

        // Set timeout
//        builder.timeout(); // Can be made configurable

        // Add headers
        request.headers().forEach((name, value) -> {
            if (value != null) {
                builder.header(name, value);
            }
        });

        // Set method and body
        switch (request.method()) {
            case GET:
                builder.GET();
                break;
            case POST:
                builder.header("Content-Type", "application/json; charset=utf-8") // TODO?
                        .POST(BodyPublishers.ofString(request.body()));
                break;
            default:
                throw new RuntimeException("Unsupported HTTP method: " + request.method());
        }

        if (readTimeout != null) {
            builder.timeout(readTimeout);
        }

        return builder.build();
    }

    private static boolean isSuccessful(java.net.http.HttpResponse<?> httpResponse) {
        int statusCode = httpResponse.statusCode();
        return statusCode >= 200 && statusCode < 300;
    }
}
