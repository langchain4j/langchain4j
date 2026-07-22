package dev.langchain4j.http.client.apache;

import static dev.langchain4j.http.client.sse.ServerSentEventListenerUtils.ignoringExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import mutiny.zero.BackpressureStrategy;
import mutiny.zero.TubeConfiguration;
import mutiny.zero.ZeroPublisher;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpEntityContainer;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;

public class ApacheHttpClient implements HttpClient {

    static final int DEFAULT_STREAMING_BUFFER_SIZE = 16384;

    private final CloseableHttpClient syncClient;
    private final CloseableHttpAsyncClient asyncClient;
    private final int streamingBufferSize;

    public ApacheHttpClient(ApacheHttpClientBuilder builder) {
        boolean syncBuilderProvidedByUser = builder.httpClientBuilder() != null;
        boolean asyncBuilderProvidedByUser = builder.httpAsyncClientBuilder() != null;
        org.apache.hc.client5.http.impl.classic.HttpClientBuilder syncHttpClientBuilder =
                getOrDefault(builder.httpClientBuilder(), HttpClients::custom);
        org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder asyncHttpClientBuilder =
                getOrDefault(builder.httpAsyncClientBuilder(), HttpAsyncClients::custom);

        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();

        if (builder.connectTimeout() != null) {
            setConnectTimeout(requestConfigBuilder, builder.connectTimeout());
        }
        if (builder.readTimeout() != null) {
            requestConfigBuilder.setResponseTimeout(
                    Timeout.ofMilliseconds(builder.readTimeout().toMillis()));
        }

        RequestConfig requestConfig = requestConfigBuilder.build();
        asyncHttpClientBuilder.setDefaultRequestConfig(requestConfig);
        syncHttpClientBuilder.setDefaultRequestConfig(requestConfig);

        if (!syncBuilderProvidedByUser) {
            syncHttpClientBuilder.disableAutomaticRetries();
        }
        if (!asyncBuilderProvidedByUser) {
            asyncHttpClientBuilder.disableAutomaticRetries();
        }

        this.syncClient = syncHttpClientBuilder.build();
        this.asyncClient = asyncHttpClientBuilder.build();
        this.asyncClient.start();
        this.streamingBufferSize = ensureGreaterThanZero(
                getOrDefault(builder.streamingBufferSize(), DEFAULT_STREAMING_BUFFER_SIZE), "streamingBufferSize");
    }

    /**
     * {@code RequestConfig.Builder.setConnectTimeout} is deprecated in favour of
     * {@code ConnectionConfig.Builder.setConnectTimeout}, but the latter can only be applied through a
     * connection manager, which would override the one a user may have configured on the supplied client builders.
     * The connect timeout from {@code RequestConfig} takes precedence over {@code ConnectionConfig} when set,
     * and is read by both the sync and the async execution runtimes.
     */
    @SuppressWarnings("deprecation")
    private static void setConnectTimeout(RequestConfig.Builder requestConfigBuilder, Duration connectTimeout) {
        requestConfigBuilder.setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()));
    }

    public static ApacheHttpClientBuilder builder() {
        return new ApacheHttpClientBuilder();
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) throws HttpException {
        try {
            ClassicHttpRequest apacheRequest = toApacheRequest(request);
            return syncClient.execute(apacheRequest, classicHttpResponse -> {
                if (!isSuccessful(classicHttpResponse)) {
                    throw new HttpException(classicHttpResponse.getCode(), readBody(classicHttpResponse));
                }
                return fromApacheResponse(classicHttpResponse);
            });
        } catch (SocketTimeoutException e) {
            throw new TimeoutException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
        SimpleHttpRequest apacheRequest = toSimpleApacheRequest(request);
        CompletableFuture<SuccessfulHttpResponse> future = new CompletableFuture<>();
        java.util.concurrent.Future<SimpleHttpResponse> apacheFuture =
                asyncClient.execute(apacheRequest, new FutureCallback<>() {
                    @Override
                    public void completed(SimpleHttpResponse apacheResponse) {
                        if (!isSuccessful(apacheResponse)) {
                            future.completeExceptionally(
                                    new HttpException(apacheResponse.getCode(), apacheResponse.getBodyText()));
                        } else {
                            future.complete(fromApacheResponse(apacheResponse));
                        }
                    }

                    @Override
                    public void failed(Exception ex) {
                        future.completeExceptionally(
                                ex instanceof SocketTimeoutException ? new TimeoutException(ex) : ex);
                    }

                    @Override
                    public void cancelled() {
                        future.cancel(true);
                    }
                });

        future.whenComplete((response, error) -> {
            if (future.isCancelled()) {
                apacheFuture.cancel(true);
            }
        });
        return future;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Incrementally streaming, like the JDK client: the response body is consumed off the socket by an
     * {@link AsyncResponseConsumer} as it arrives, bytes are pushed through the parser's
     * {@link ServerSentEventParser#incremental() incremental} mode, and each completed event is delivered
     * immediately — nothing is buffered to end-of-response, and no thread is pinned for the lifetime of the
     * stream. On any terminal signal (a downstream cancel, an error, or a buffer overflow) no further events are
     * delivered and cancellation of the in-flight request is requested; note that Apache's async client does not
     * always close the underlying connection promptly on cancellation (see {@code cancelAbortsConnection} in the
     * client's cancellation contract test), whereas event delivery stops immediately.
     */
    @Override
    public Flow.Publisher<HttpStreamingEvent> stream(HttpRequest request, ServerSentEventParser parser) {
        TubeConfiguration config = new TubeConfiguration()
                .withBackpressureStrategy(BackpressureStrategy.BUFFER)
                .withBufferSize(streamingBufferSize);
        return ZeroPublisher.create(config, tube -> {
            ServerSentEventListener listener = new ServerSentEventListener() {
                @Override
                public void onOpen(SuccessfulHttpResponse response) {
                    if (!tube.cancelled()) {
                        tube.send(new HttpResponseReceived(response));
                    }
                }

                @Override
                public void onEvent(ServerSentEvent event) {
                    if (!tube.cancelled()) {
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
                public void onClose() {
                    if (!tube.cancelled()) {
                        tube.complete();
                    }
                }
            };
            java.util.concurrent.Future<Void> future = asyncClient.execute(
                    SimpleRequestProducer.create(toSimpleApacheRequest(request)),
                    new SseResponseConsumer(parser, listener),
                    null);
            tube.whenTerminates(() -> future.cancel(true));
        });
    }

    /**
     * Streaming {@link AsyncResponseConsumer} that drives a {@link ServerSentEventListener} incrementally: the
     * reactor pushes body bytes as they arrive off the socket, they are fed to the parser's
     * {@link ServerSentEventParser.Incremental incremental} mode, and each completed event is delivered right
     * away. A non-2xx response body is buffered (it is small) so it can be reported through {@link HttpException}.
     */
    private static final class SseResponseConsumer implements AsyncResponseConsumer<Void> {

        private final ServerSentEventParser parser;
        private final ServerSentEventListener listener;

        private ServerSentEventParser.Incremental incremental;
        private FutureCallback<Void> resultCallback;
        private int errorStatus;
        private ByteArrayOutputStream errorBody;

        SseResponseConsumer(ServerSentEventParser parser, ServerSentEventListener listener) {
            this.parser = parser;
            this.listener = listener;
        }

        @Override
        public void consumeResponse(
                HttpResponse response,
                EntityDetails entityDetails,
                HttpContext context,
                FutureCallback<Void> resultCallback) {
            this.resultCallback = resultCallback;
            int status = response.getCode();
            if (status >= 200 && status < 300) {
                incremental = parser.incremental();
                // The SSE body is delivered incrementally as events, not as a buffered byte[].
                SuccessfulHttpResponse open = successfulResponseFrom(response, null);
                ignoringExceptions(() -> listener.onOpen(open));
                if (entityDetails == null) {
                    finishOk();
                }
            } else {
                errorStatus = status;
                errorBody = new ByteArrayOutputStream();
                if (entityDetails == null) {
                    finishError();
                }
            }
        }

        @Override
        public void informationResponse(HttpResponse response, HttpContext context) {}

        @Override
        public void updateCapacity(CapacityChannel capacityChannel) throws IOException {
            capacityChannel.update(Integer.MAX_VALUE);
        }

        @Override
        public void consume(ByteBuffer src) {
            if (incremental != null) {
                for (ServerSentEvent event : incremental.feed(src)) {
                    ignoringExceptions(() -> listener.onEvent(event));
                }
            } else if (errorBody != null) {
                byte[] chunk = new byte[src.remaining()];
                src.get(chunk);
                errorBody.writeBytes(chunk);
            }
        }

        @Override
        public void streamEnd(List<? extends Header> trailers) {
            if (incremental != null) {
                for (ServerSentEvent event : incremental.flush()) {
                    ignoringExceptions(() -> listener.onEvent(event));
                }
                finishOk();
            } else if (errorBody != null) {
                finishError();
            }
        }

        @Override
        public void failed(Exception cause) {
            Throwable mapped = cause instanceof SocketTimeoutException ? new TimeoutException(cause) : cause;
            ignoringExceptions(() -> listener.onError(mapped));
        }

        @Override
        public void releaseResources() {}

        private void finishOk() {
            ignoringExceptions(listener::onClose);
            if (resultCallback != null) {
                resultCallback.completed(null);
            }
        }

        private void finishError() {
            HttpException ex = new HttpException(errorStatus, errorBody.toString(StandardCharsets.UTF_8));
            ignoringExceptions(() -> listener.onError(ex));
            if (resultCallback != null) {
                resultCallback.failed(ex);
            }
        }
    }

    private static SuccessfulHttpResponse successfulResponseFrom(HttpResponse response, byte[] body) {
        Map<String, List<String>> headers = new HashMap<>();
        for (Header header : response.getHeaders()) {
            headers.computeIfAbsent(header.getName(), k -> new ArrayList<>()).add(header.getValue());
        }
        return SuccessfulHttpResponse.builder()
                .statusCode(response.getCode())
                .headers(headers)
                .body(body)
                .build();
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        executeServerSentEvents(request, parser, listener);
    }

    /**
     * Starts a server-sent-events request and returns the in-flight Apache {@link java.util.concurrent.Future}, so
     * a caller (the reactive {@link #stream}) can abort the underlying HTTP request. The listener callbacks are
     * unchanged.
     */
    private java.util.concurrent.Future<SimpleHttpResponse> executeServerSentEvents(
            HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        SimpleHttpRequest apacheRequest = toSimpleApacheRequest(request);
        return asyncClient.execute(apacheRequest, new FutureCallback<>() {
            @Override
            public void completed(SimpleHttpResponse apacheResponse) {
                if (!isSuccessful(apacheResponse)) {
                    HttpException exception = new HttpException(apacheResponse.getCode(), apacheResponse.getBodyText());
                    ignoringExceptions(() -> listener.onError(exception));
                    return;
                }

                SuccessfulHttpResponse response = fromApacheResponse(apacheResponse);
                ignoringExceptions(() -> listener.onOpen(response));

                try (InputStream inputStream = getInputStream(apacheResponse)) {
                    parser.parse(inputStream, listener);
                    ignoringExceptions(listener::onClose);
                } catch (Exception e) {
                    ignoringExceptions(() -> listener.onError(e));
                }
            }

            @Override
            public void failed(Exception ex) {
                if (ex instanceof SocketTimeoutException) {
                    ignoringExceptions(() -> listener.onError(new TimeoutException(ex)));
                } else {
                    ignoringExceptions(() -> listener.onError(ex));
                }
            }

            @Override
            public void cancelled() {}
        });
    }

    private InputStream getInputStream(SimpleHttpResponse apacheResponse) {
        byte[] bodyBytes = apacheResponse.getBody().getBodyBytes();
        return new ByteArrayInputStream(Objects.requireNonNullElseGet(bodyBytes, () -> new byte[0]));
    }

    private SuccessfulHttpResponse fromApacheResponse(ClassicHttpResponse httpResponse) throws IOException {
        Map<String, List<String>> headers = new HashMap<>();
        org.apache.hc.core5.http.Header[] allHeaders = httpResponse.getHeaders();
        for (org.apache.hc.core5.http.Header header : allHeaders) {
            headers.computeIfAbsent(header.getName(), k -> new ArrayList<>()).add(header.getValue());
        }
        return SuccessfulHttpResponse.builder()
                .statusCode(httpResponse.getCode())
                .headers(headers)
                .body(readBodyBytes(httpResponse))
                .build();
    }

    private byte[] readBodyBytes(HttpEntityContainer httpEntityContainer) throws IOException {
        HttpEntity entity = httpEntityContainer.getEntity();
        if (entity == null) {
            return new byte[0];
        }
        return EntityUtils.toByteArray(entity);
    }

    private String readBody(HttpEntityContainer httpEntityContainer) {
        try {
            HttpEntity entity = httpEntityContainer.getEntity();
            if (entity == null) {
                return "";
            }
            try (InputStream inputStream = entity.getContent();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                return reader.lines().collect(joining(System.lineSeparator()));
            }
        } catch (Exception e) {
            return "Cannot read error response body: " + e.getMessage();
        }
    }

    private boolean isSuccessful(HttpResponse httpResponse) {
        int statusCode = httpResponse.getCode();
        return statusCode >= 200 && statusCode < 300;
    }

    private ClassicHttpRequest toApacheRequest(HttpRequest request) {
        ClassicHttpRequest apacheRequest =
                switch (request.method()) {
                    case GET -> new HttpGet(request.url());
                    case DELETE -> new HttpDelete(request.url());
                    case POST -> new HttpPost(request.url());
                };

        if (request.formDataFields().isEmpty() && request.formDataFiles().isEmpty()) {
            if (request.body() != null) {
                apacheRequest.setEntity(new StringEntity(request.body(), ContentType.APPLICATION_JSON));
            }
        } else {
            HttpEntity entity =
                    MultipartBodyPublisher.buildMultipartEntity(request.formDataFields(), request.formDataFiles());
            apacheRequest.setEntity(entity);
        }

        request.headers().forEach((name, values) -> {
            if (values != null) {
                for (String value : values) {
                    apacheRequest.addHeader(name, value);
                }
            }
        });

        return apacheRequest;
    }

    private SimpleHttpRequest toSimpleApacheRequest(HttpRequest request) {
        SimpleRequestBuilder builder;
        String uri = request.url();

        if (request.formDataFields().isEmpty() && request.formDataFiles().isEmpty()) {
            builder = switch (request.method()) {
                case GET -> SimpleRequestBuilder.get(uri);
                case DELETE -> SimpleRequestBuilder.delete(uri);
                case POST -> SimpleRequestBuilder.post(uri);
            };

            if (request.body() != null) {
                builder.setBody(request.body(), ContentType.APPLICATION_JSON);
            }
        } else {
            builder = SimpleRequestBuilder.post(uri);
            HttpEntity entity =
                    MultipartBodyPublisher.buildMultipartEntity(request.formDataFields(), request.formDataFiles());
            try {
                byte[] bytes = EntityUtils.toByteArray(entity);
                String contentTypeStr = entity.getContentType();
                ContentType contentType =
                        contentTypeStr != null ? ContentType.parse(contentTypeStr) : ContentType.MULTIPART_FORM_DATA;
                builder.setBody(bytes, contentType);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        request.headers().forEach((name, values) -> {
            if (values != null) {
                for (String value : values) {
                    builder.addHeader(name, value);
                }
            }
        });

        return builder.build();
    }

    private SuccessfulHttpResponse fromApacheResponse(SimpleHttpResponse apacheResponse) {
        Map<String, List<String>> headers = new HashMap<>();
        org.apache.hc.core5.http.Header[] allHeaders = apacheResponse.getHeaders();
        for (org.apache.hc.core5.http.Header header : allHeaders) {
            headers.computeIfAbsent(header.getName(), k -> new ArrayList<>()).add(header.getValue());
        }

        String contentType = null;
        for (org.apache.hc.core5.http.Header header : allHeaders) {
            if ("content-type".equalsIgnoreCase(header.getName())) {
                contentType = header.getValue();
                break;
            }
        }

        byte[] body;
        if (contentType != null && contentType.contains("text/event-stream")) {
            body = null;
        } else {
            body = apacheResponse.getBodyBytes();
        }

        return SuccessfulHttpResponse.builder()
                .statusCode(apacheResponse.getCode())
                .headers(headers)
                .body(body)
                .build();
    }
}
