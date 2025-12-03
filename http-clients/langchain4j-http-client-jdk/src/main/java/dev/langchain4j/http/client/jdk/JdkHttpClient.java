package dev.langchain4j.http.client.jdk;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.http.client.sse.StreamingHttpEvent;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.TimeUnit;

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
        } catch (IOException | InterruptedException e) {
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

    @Override
    public Publisher<List<ByteBuffer>> executeWithPublisherRaw(HttpRequest request) {
        return null; // TODO
    }

    java.net.http.HttpRequest toJdkRequest(HttpRequest request) {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(request.url()));

        request.headers().forEach((name, values) -> {
            if (values != null) {
                values.forEach(value -> builder.header(name, value));
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

    static class StreamingHttpEventPublisher implements Publisher<StreamingHttpEvent> { // TODO name, location

        private final java.net.http.HttpClient client;
        private final java.net.http.HttpRequest request;

        StreamingHttpEventPublisher(java.net.http.HttpClient client, java.net.http.HttpRequest request) {
            this.client = ensureNotNull(client, "client");
            this.request = ensureNotNull(request, "request");
        }

        @Override
        public void subscribe(Subscriber<? super StreamingHttpEvent> subscriber) {
            // TODO should send request on subscription or on first request(n)?
            // TODO verify there is only one subscription

            client.sendAsync(request, BodyHandlers.ofPublisher())
                    .thenAccept(jdkResponse -> handleResponse(jdkResponse, subscriber))
                    .exceptionally(throwable -> {
                        handleException(throwable, subscriber);
                        return null;
                    });
        }

        private void handleResponse(
                java.net.http.HttpResponse<Publisher<List<ByteBuffer>>> jdkResponse,
                Subscriber<? super StreamingHttpEvent> subscriber) {

            if (!isSuccessful(jdkResponse)) {
                String errorMessage = "Request failed";
                try {
                    errorMessage = readErrorBody(jdkResponse.body());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                subscriber.onError(new HttpException(jdkResponse.statusCode(), errorMessage));
                return;
            }

            SuccessfulHttpResponse response = fromJdkResponse(jdkResponse, null);

            jdkResponse.body().subscribe(new SSEBodySubscriber(subscriber, response));
        }

        private String readErrorBody(Publisher<List<ByteBuffer>> publisher) throws Exception { // TODO reimplement
            var future = new CompletableFuture<String>();

            publisher.subscribe(new Subscriber<>() {

                private final ByteArrayOutputStream out = new ByteArrayOutputStream();
                private Subscription subscription;

                @Override
                public void onSubscribe(Subscription subscription) {
                    this.subscription = subscription;
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(List<ByteBuffer> item) {
                    try {
                        for (ByteBuffer buffer : item) {
                            byte[] bytes = new byte[buffer.remaining()];
                            buffer.get(bytes);
                            out.write(bytes);
                        }
                    } catch (Exception ex) {
                        future.completeExceptionally(ex);
                        subscription.cancel();
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    future.completeExceptionally(throwable);
                }

                @Override
                public void onComplete() {
                    future.complete(out.toString(StandardCharsets.UTF_8));
                }
            });

            return future.get(30, TimeUnit.SECONDS);
        }

        private void handleException(Throwable throwable, Subscriber<? super StreamingHttpEvent> subscriber) {
            if (throwable.getCause() instanceof HttpTimeoutException) {
                subscriber.onError(new TimeoutException(throwable));
            } else {
                subscriber.onError(throwable);
            }
        }
    }

    private static class SSEBodySubscriber implements Subscriber<List<ByteBuffer>> {

        private final Subscriber<? super StreamingHttpEvent> downstream;
        private final SuccessfulHttpResponse response;
        private Subscription subscription;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private volatile boolean openEventSent = false;

        SSEBodySubscriber(Subscriber<? super StreamingHttpEvent> downstream, SuccessfulHttpResponse response) {
            this.downstream = downstream;
            this.response = response;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;

            downstream.onSubscribe(new Subscription() {

                @Override
                public void request(long n) {
                    if (!openEventSent) {
                        openEventSent = true;
                        downstream.onNext(response);
                    }
                    subscription.request(n); // TODO n or n-1 if response was sent?
                }

                @Override
                public void cancel() {
                    subscription.cancel();
                }
            });
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            try {
                for (ByteBuffer buf : buffers) {
                    byte[] bytes = new byte[buf.remaining()];
                    buf.get(bytes);
                    buffer.write(bytes);
                }

                parseAndEmitEvents();
//                subscription.request(1); // TODO?

            } catch (Exception e) {
                subscription.cancel();
                downstream.onError(e);
            }
        }

        private void parseAndEmitEvents() {
            String data = buffer.toString(StandardCharsets.UTF_8);
//            System.out.println("OLOLO BUFFER: " + data);

            List<ServerSentEvent> serverSentEvents = parseChunk(data);
            for (ServerSentEvent serverSentEvent : serverSentEvents) {
                downstream.onNext(serverSentEvent); // TODO implement buffering, send only requested events
            }

            buffer.reset(); // TODO?
        }

        // TODO reimplement
        private StringBuilder incompleteLineBuffer = new StringBuilder();
        private String currentEvent = null;
        private StringBuilder currentData = new StringBuilder();

        public List<ServerSentEvent> parseChunk(String chunk) {
            List<ServerSentEvent> events = new ArrayList<>();

            // Append chunk to any incomplete line from previous chunk
            incompleteLineBuffer.append(chunk);
            String buffer = incompleteLineBuffer.toString();

            // Split by newlines but keep incomplete last line
            String[] lines = buffer.split("\n", -1);

            // Process all complete lines (all except the last one which might be incomplete)
            for (int i = 0; i < lines.length - 1; i++) {
                String line = lines[i].trim();
                processLine(line, events);
            }

            // Keep the last line (might be incomplete) for next chunk
            incompleteLineBuffer.setLength(0);
            if (lines.length > 0) {
                incompleteLineBuffer.append(lines[lines.length - 1]);
            }

            return events;
        }

        public List<ServerSentEvent> flush() { // TODO?
            List<ServerSentEvent> events = new ArrayList<>();

            // Process any remaining incomplete line
            if (incompleteLineBuffer.length() > 0) {
                String line = incompleteLineBuffer.toString().trim();
                processLine(line, events);
                incompleteLineBuffer.setLength(0);
            }

            // Emit final event if data buffer has content
            if (!currentData.isEmpty()) {
                events.add(new ServerSentEvent(currentEvent, currentData.toString()));
                currentEvent = null;
                currentData.setLength(0);
            }

            return events;
        }

        private void processLine(String line, List<ServerSentEvent> events) {
            if (line.isEmpty()) {
                // Empty line signals end of event
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
            // Ignore other SSE fields (id:, retry:, comment lines starting with :)
        }

        @Override
        public void onError(Throwable throwable) {
            downstream.onError(throwable);
        }

        @Override
        public void onComplete() {
//            downstream.onNext(new StreamingHttpEvent.Close()); TODO
            downstream.onComplete();
        }
    }
}
