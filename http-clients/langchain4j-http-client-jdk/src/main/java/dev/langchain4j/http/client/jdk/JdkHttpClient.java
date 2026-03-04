package dev.langchain4j.http.client.jdk;

import static dev.langchain4j.http.client.sse.ServerSentEventListenerUtils.ignoringExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.FormDataFile;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

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

    public static JdkHttpClientBuilder builder() {
        return new JdkHttpClientBuilder();
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) throws HttpException {
        try {
            java.net.http.HttpRequest jdkRequest = toJdkRequest(request);

            java.net.http.HttpResponse<String> jdkResponse = delegate.send(jdkRequest, BodyHandlers.ofString());

            if (!isSuccessful(jdkResponse)) {
                throw new HttpException(jdkResponse.statusCode(), jdkResponse.body());
            }

            return fromJdkResponse(jdkResponse, jdkResponse.body());
        } catch (HttpTimeoutException e) {
            throw new TimeoutException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();  
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        java.net.http.HttpRequest jdkRequest = toJdkRequest(request);

        delegate.sendAsync(jdkRequest, BodyHandlers.ofInputStream())
                .thenAccept(jdkResponse -> {
                    if (!isSuccessful(jdkResponse)) {
                        HttpException exception = new HttpException(jdkResponse.statusCode(), readBody(jdkResponse));
                        ignoringExceptions(() -> listener.onError(exception));
                        return;
                    }

                    SuccessfulHttpResponse response = fromJdkResponse(jdkResponse, null);
                    ignoringExceptions(() -> listener.onOpen(response));

                    try (InputStream inputStream = jdkResponse.body()) {
                        parser.parse(inputStream, listener);
                        ignoringExceptions(listener::onClose);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .exceptionally(throwable -> {
                    if (throwable.getCause() instanceof HttpTimeoutException) {
                        ignoringExceptions(() -> listener.onError(new TimeoutException(throwable)));
                    } else {
                        ignoringExceptions(() -> listener.onError(throwable));
                    }
                    return null;
                });
    }

    private java.net.http.HttpRequest toJdkRequest(HttpRequest request) {
        java.net.http.HttpRequest.Builder builder =
                java.net.http.HttpRequest.newBuilder().uri(URI.create(request.url()));

        request.headers().forEach((name, values) -> {
            if (values != null) {
                values.forEach(value -> builder.header(name, value));
            }
        });

        BodyPublisher bodyPublisher;
        if (request.formDataFields().isEmpty() && request.formDataFiles().isEmpty()) {
            if (request.body() != null) {
                bodyPublisher = BodyPublishers.ofString(request.body());
            } else {
                bodyPublisher = BodyPublishers.noBody();
            }
        } else {
            bodyPublisher = ofMultipartData(request.formDataFields(), request.formDataFiles());
        }
        builder.method(request.method().name(), bodyPublisher);

        if (readTimeout != null) {
            builder.timeout(readTimeout);
        }

        return builder.build();
    }

    private static BodyPublisher ofMultipartData(Map<String, String> fields, Map<String, FormDataFile> files) {
        MultipartBodyPublisher publisher = new MultipartBodyPublisher();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            publisher.addField(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, FormDataFile> entry : files.entrySet()) {
            publisher.addFile(entry.getKey(), entry.getValue());
        }
        return publisher.build();
    }

    private static SuccessfulHttpResponse fromJdkResponse(java.net.http.HttpResponse<?> response, String body) {
        return SuccessfulHttpResponse.builder()
                .statusCode(response.statusCode())
                .headers(response.headers().map())
                .body(body)
                .build();
    }

    private static boolean isSuccessful(java.net.http.HttpResponse<?> response) {
        int statusCode = response.statusCode();
        return statusCode >= 200 && statusCode < 300;
    }

    private static String readBody(java.net.http.HttpResponse<InputStream> response) {
        try (InputStream inputStream = response.body();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines().collect(joining(System.lineSeparator()));
        } catch (IOException e) {
            return "Cannot read error response body: " + e.getMessage();
        }
    }
}
