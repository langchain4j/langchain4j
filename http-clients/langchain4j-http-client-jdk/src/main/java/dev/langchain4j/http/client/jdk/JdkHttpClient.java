package dev.langchain4j.http.client.jdk;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.FormDataFile;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.http.client.sse.StreamingHttpEvent;

import mutiny.zero.BackpressureStrategy;
import mutiny.zero.Tube;
import mutiny.zero.TubeConfiguration;
import mutiny.zero.ZeroPublisher;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;

import static dev.langchain4j.http.client.sse.ServerSentEventListenerUtils.ignoringExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.joining;

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

    @Override
    public Publisher<StreamingHttpEvent> executeWithPublisher(HttpRequest request) {
        return new StreamingHttpEventPublisher(delegate, toJdkRequest(request));
    }

    java.net.http.HttpRequest toJdkRequest(HttpRequest request) {
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

    static class StreamingHttpEventPublisher implements Publisher<StreamingHttpEvent> {

        private static final int TUBE_BUFFER_SIZE = 1024;

        private final java.net.http.HttpClient client;
        private final java.net.http.HttpRequest request;

        StreamingHttpEventPublisher(java.net.http.HttpClient client, java.net.http.HttpRequest request) {
            this.client = ensureNotNull(client, "client");
            this.request = ensureNotNull(request, "request");
        }

        @Override
        public void subscribe(Subscriber<? super StreamingHttpEvent> subscriber) {
            if (subscriber == null) {
                throw new NullPointerException("subscriber");
            }

            TubeConfiguration config = new TubeConfiguration()
                    .withBackpressureStrategy(BackpressureStrategy.BUFFER)
                    .withBufferSize(TUBE_BUFFER_SIZE);

            Publisher<StreamingHttpEvent> publisher = ZeroPublisher.create(config, tube -> {
                CompletableFuture<HttpResponse<Publisher<List<ByteBuffer>>>> future =
                        client.sendAsync(request, BodyHandlers.ofPublisher());

                tube.whenCancelled(() -> future.cancel(true));

                future.thenAccept(jdkResponse -> {
                    if (tube.cancelled()) {
                        return;
                    }
                    if (!isSuccessful(jdkResponse)) {
                        readErrorBodyAsync(jdkResponse.body()).whenComplete((body, err) -> {
                            if (err != null) {
                                tube.fail(err);
                            } else {
                                tube.fail(new HttpException(jdkResponse.statusCode(), body));
                            }
                        });
                        return;
                    }
                    tube.send(fromJdkResponse(jdkResponse, null));
                    jdkResponse.body().subscribe(new SseByteToEventSubscriber(tube));
                }).exceptionally(throwable -> {
                    if (!tube.cancelled()) {
                        Throwable cause = throwable.getCause();
                        if (cause instanceof HttpTimeoutException) {
                            tube.fail(new TimeoutException(throwable));
                        } else {
                            tube.fail(throwable);
                        }
                    }
                    return null;
                });
            });

            publisher.subscribe(subscriber);
        }

        private static CompletableFuture<String> readErrorBodyAsync(Publisher<List<ByteBuffer>> body) {
            CompletableFuture<String> future = new CompletableFuture<>();
            body.subscribe(new Subscriber<>() {
                private final ByteArrayOutputStream out = new ByteArrayOutputStream();

                @Override
                public void onSubscribe(Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(List<ByteBuffer> buffers) {
                    for (ByteBuffer buf : buffers) {
                        byte[] bytes = new byte[buf.remaining()];
                        buf.get(bytes);
                        out.write(bytes, 0, bytes.length);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    future.complete("Cannot read error response body: " + throwable.getMessage());
                }

                @Override
                public void onComplete() {
                    future.complete(out.toString(StandardCharsets.UTF_8));
                }
            });
            return future;
        }
    }

    private static class SseByteToEventSubscriber implements Subscriber<List<ByteBuffer>> {

        private final Tube<StreamingHttpEvent> tube;
        private final StringBuilder incompleteLineBuffer = new StringBuilder();
        private final StringBuilder currentData = new StringBuilder();
        private String currentEvent;
        private Subscription subscription;

        SseByteToEventSubscriber(Tube<StreamingHttpEvent> tube) {
            this.tube = tube;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            if (tube.cancelled()) {
                subscription.cancel();
                return;
            }
            tube.whenCancelled(subscription::cancel);
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (tube.cancelled()) {
                return;
            }
            StringBuilder chunk = new StringBuilder();
            for (ByteBuffer buf : buffers) {
                byte[] bytes = new byte[buf.remaining()];
                buf.get(bytes);
                chunk.append(new String(bytes, StandardCharsets.UTF_8));
            }
            for (ServerSentEvent event : parseChunk(chunk.toString())) {
                tube.send(event);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (!tube.cancelled()) {
                tube.fail(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (tube.cancelled()) {
                return;
            }
            for (ServerSentEvent event : flush()) {
                tube.send(event);
            }
            tube.complete();
        }

        private List<ServerSentEvent> parseChunk(String chunk) {
            List<ServerSentEvent> events = new ArrayList<>();
            incompleteLineBuffer.append(chunk);
            String buffer = incompleteLineBuffer.toString();
            String[] lines = buffer.split("\n", -1);
            for (int i = 0; i < lines.length - 1; i++) {
                processLine(lines[i].trim(), events);
            }
            incompleteLineBuffer.setLength(0);
            if (lines.length > 0) {
                incompleteLineBuffer.append(lines[lines.length - 1]);
            }
            return events;
        }

        private List<ServerSentEvent> flush() {
            List<ServerSentEvent> events = new ArrayList<>();
            if (incompleteLineBuffer.length() > 0) {
                processLine(incompleteLineBuffer.toString().trim(), events);
                incompleteLineBuffer.setLength(0);
            }
            if (!currentData.isEmpty()) {
                events.add(new ServerSentEvent(currentEvent, currentData.toString()));
                currentEvent = null;
                currentData.setLength(0);
            }
            return events;
        }

        private void processLine(String line, List<ServerSentEvent> events) {
            if (line.isEmpty()) {
                if (!currentData.isEmpty()) {
                    events.add(new ServerSentEvent(currentEvent, currentData.toString()));
                    currentEvent = null;
                    currentData.setLength(0);
                }
                return;
            }
            if (line.startsWith("event:")) {
                currentEvent = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                String content = line.substring("data:".length());
                if (!currentData.isEmpty()) {
                    currentData.append("\n");
                }
                currentData.append(content.trim());
            }
        }
    }
}
