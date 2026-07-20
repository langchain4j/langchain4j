package dev.langchain4j.http.client.jdk;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.FormDataFile;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import mutiny.zero.BackpressureStrategy;
import mutiny.zero.TubeConfiguration;
import mutiny.zero.ZeroPublisher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.http.client.sse.ServerSentEventListenerUtils.ignoringExceptions;
import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class JdkHttpClient implements HttpClient {

    static final int DEFAULT_STREAMING_BUFFER_SIZE = 16384;

    private final java.net.http.HttpClient delegate;
    private final Duration readTimeout;
    private final int streamingBufferSize;

    public JdkHttpClient(JdkHttpClientBuilder builder) {
        java.net.http.HttpClient.Builder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder(), java.net.http.HttpClient::newBuilder);
        if (builder.connectTimeout() != null) {
            httpClientBuilder.connectTimeout(builder.connectTimeout());
        }
        this.delegate = httpClientBuilder.build();
        this.readTimeout = builder.readTimeout();
        this.streamingBufferSize = ensureGreaterThanZero(getOrDefault(builder.streamingBufferSize(), DEFAULT_STREAMING_BUFFER_SIZE), "streamingBufferSize");
    }

    public static JdkHttpClientBuilder builder() {
        return new JdkHttpClientBuilder();
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) throws HttpException {
        try {
            java.net.http.HttpRequest jdkRequest = toJdkRequest(request);

            java.net.http.HttpResponse<byte[]> jdkResponse = delegate.send(jdkRequest, BodyHandlers.ofByteArray());

            if (!isSuccessful(jdkResponse)) {
                throw new HttpException(
                        jdkResponse.statusCode(), new String(jdkResponse.body(), StandardCharsets.UTF_8));
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
    public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
        java.net.http.HttpRequest jdkRequest = toJdkRequest(request);

        CompletableFuture<HttpResponse<byte[]>> sendFuture =
                delegate.sendAsync(jdkRequest, BodyHandlers.ofByteArray());

        CompletableFuture<SuccessfulHttpResponse> result = new CompletableFuture<>();
        sendFuture.whenComplete((jdkResponse, throwable) -> {
            if (throwable != null) {
                Throwable cause = unwrapCompletionException(throwable);
                if (cause instanceof HttpTimeoutException) {
                    result.completeExceptionally(new TimeoutException(cause));
                } else {
                    result.completeExceptionally(cause);
                }
            } else if (!isSuccessful(jdkResponse)) {
                result.completeExceptionally(new HttpException(
                        jdkResponse.statusCode(), new String(jdkResponse.body(), StandardCharsets.UTF_8)));
            } else {
                result.complete(fromJdkResponse(jdkResponse, jdkResponse.body()));
            }
        });

        propagateCancellation(result, sendFuture);
        return result;
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
    public Publisher<HttpStreamingEvent> stream(HttpRequest request, ServerSentEventParser parser) {
        return new HttpStreamingEventPublisher(delegate, toJdkRequest(request), parser, streamingBufferSize);
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
            builder.setHeader("Content-Type", MultipartBodyPublisher.contentType());
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

    private static SuccessfulHttpResponse fromJdkResponse(java.net.http.HttpResponse<?> response, byte[] body) {
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
        try (InputStream inputStream = response.body()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Cannot read error response body: " + e.getMessage();
        }
    }

    /**
     * Cold {@link Publisher} that drives the JDK {@code HttpClient} on each subscribe and delivers
     * parsed {@link HttpStreamingEvent}s through a Mutiny Zero {@code Tube}. Fully non-blocking:
     * the response body arrives as a {@link Publisher}{@code <List<ByteBuffer>>} from the JDK
     * (via {@code BodyHandlers.ofPublisher()}), bytes are pushed through the supplied
     * {@link ServerSentEventParser}'s incremental mode, and complete events are forwarded to the
     * downstream subscriber. No thread is pinned for the lifetime of the stream.
     */
    static class HttpStreamingEventPublisher implements Publisher<HttpStreamingEvent> {

        private final java.net.http.HttpClient client;
        private final java.net.http.HttpRequest request;
        private final ServerSentEventParser parser;
        private final int bufferSize;

        HttpStreamingEventPublisher(java.net.http.HttpClient client,
                                    java.net.http.HttpRequest request,
                                    ServerSentEventParser parser,
                                    int bufferSize) {
            this.client = ensureNotNull(client, "client");
            this.request = ensureNotNull(request, "request");
            this.parser = ensureNotNull(parser, "parser");
            this.bufferSize = bufferSize;
        }

        @Override
        public void subscribe(Subscriber<? super HttpStreamingEvent> subscriber) {
            if (subscriber == null) {
                throw new NullPointerException("subscriber");
            }

            TubeConfiguration config = new TubeConfiguration()
                    .withBackpressureStrategy(BackpressureStrategy.BUFFER)
                    .withBufferSize(bufferSize);

            Publisher<HttpStreamingEvent> publisher = ZeroPublisher.create(config, tube -> {
                CompletableFuture<HttpResponse<Publisher<List<ByteBuffer>>>> future =
                        client.sendAsync(request, BodyHandlers.ofPublisher());

                // Any terminal signal must release the HTTP call: the future (if still pending headers) and the
                // body subscription (if we're already streaming). Using whenTerminates rather than whenCancelled
                // covers not only a downstream cancel but also a buffer overflow (which fails the tube internally) -
                // so a slow consumer that overflows the buffer aborts the socket read instead of draining it to EOF.
                // On a normal completion the future/subscription are already done, so the cancels are no-ops.
                AtomicReference<Subscription> bodySubRef = new AtomicReference<>();
                tube.whenTerminates(() -> {
                    future.cancel(true);
                    Subscription bodySub = bodySubRef.get();
                    if (bodySub != null) {
                        bodySub.cancel();
                    }
                });

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
                    tube.send(new HttpResponseReceived(fromJdkResponse(jdkResponse, null)));

                    ServerSentEventParser.Incremental incremental = parser.incremental();
                    jdkResponse.body().subscribe(new Subscriber<>() {

                        @Override
                        public void onSubscribe(Subscription subscription) {
                            bodySubRef.set(subscription);
                            if (tube.cancelled()) {
                                subscription.cancel();
                                return;
                            }
                            // Unbounded demand on purpose. The JDK client's demand maps to TCP / HTTP-2 flow
                            // control, so we *could* throttle the socket — but it is pointless here: it cannot
                            // slow token generation (already produced and billed server-side), and stalling the
                            // read risks an idle-timeout reset mid-stream. Heap is bounded by the Tube buffer
                            // downstream: on overflow the tube fails, and the whenTerminates hook above then
                            // cancels this body subscription and aborts the socket read.
                            subscription.request(Long.MAX_VALUE);
                        }

                        @Override
                        public void onNext(List<ByteBuffer> buffers) {
                            if (tube.cancelled()) {
                                return;
                            }
                            try {
                                for (ByteBuffer buf : buffers) {
                                    for (ServerSentEvent event : incremental.feed(buf)) {
                                        tube.send(event);
                                    }
                                }
                            } catch (Exception e) {
                                Subscription bodySub = bodySubRef.get();
                                if (bodySub != null) bodySub.cancel();
                                if (!tube.cancelled()) tube.fail(e);
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
                            try {
                                for (ServerSentEvent event : incremental.flush()) {
                                    tube.send(event);
                                }
                                tube.complete();
                            } catch (Exception e) {
                                if (!tube.cancelled()) tube.fail(e);
                            }
                        }
                    });
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
}
