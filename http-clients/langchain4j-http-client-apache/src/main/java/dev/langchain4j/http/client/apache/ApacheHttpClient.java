package dev.langchain4j.http.client.apache;

import static dev.langchain4j.http.client.sse.ServerSentEventListenerUtils.ignoringExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEventParsingHandle;
import dev.langchain4j.http.client.sse.StreamingHttpEvent;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import mutiny.zero.BackpressureStrategy;
import mutiny.zero.TubeConfiguration;
import mutiny.zero.ZeroPublisher;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
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
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpEntityContainer;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

public class ApacheHttpClient implements HttpClient {

    private final CloseableHttpClient syncClient;
    private final CloseableHttpAsyncClient asyncClient;

    public ApacheHttpClient(ApacheHttpClientBuilder builder) {
        org.apache.hc.client5.http.impl.classic.HttpClientBuilder syncHttpClientBuilder =
                getOrDefault(builder.httpClientBuilder(), HttpClients::custom);
        org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder asyncHttpClientBuilder =
                getOrDefault(builder.httpAsyncClientBuilder(), HttpAsyncClients::custom);

        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();

        if (builder.connectTimeout() != null) {
            requestConfigBuilder.setConnectionRequestTimeout(
                    Timeout.ofMilliseconds(builder.connectTimeout().toMillis()));
        }
        if (builder.readTimeout() != null) {
            requestConfigBuilder.setResponseTimeout(
                    Timeout.ofMilliseconds(builder.readTimeout().toMillis()));
        }

        RequestConfig requestConfig = requestConfigBuilder.build();
        asyncHttpClientBuilder.setDefaultRequestConfig(requestConfig);
        syncHttpClientBuilder.setDefaultRequestConfig(requestConfig);
        this.syncClient = syncHttpClientBuilder.build();
        this.asyncClient = asyncHttpClientBuilder.build();
        this.asyncClient.start();
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
                future.completeExceptionally(ex instanceof SocketTimeoutException ? new TimeoutException(ex) : ex);
            }

            @Override
            public void cancelled() {
                future.cancel(true);
            }
        });
        return future;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note: the Apache async client buffers the whole response before delivering it, so — unlike the JDK
     * client — this publisher does not stream incrementally; it emits all parsed events once the body has
     * arrived. Cancelling the subscription cancels the SSE parsing. TODO
     */
    @Override
    public Flow.Publisher<StreamingHttpEvent> executeWithPublisher(HttpRequest request, ServerSentEventParser parser) {
        TubeConfiguration config = new TubeConfiguration()
                .withBackpressureStrategy(BackpressureStrategy.BUFFER)
                .withBufferSize(256);
        return ZeroPublisher.create(config, tube -> {
            AtomicReference<ServerSentEventParsingHandle> parsingHandle = new AtomicReference<>();
            tube.whenCancelled(() -> {
                ServerSentEventParsingHandle handle = parsingHandle.get();
                if (handle != null) {
                    handle.cancel();
                }
            });
            execute(request, parser, new ServerSentEventListener() {
                @Override
                public void onOpen(SuccessfulHttpResponse response) {
                    if (!tube.cancelled()) {
                        tube.send(response);
                    }
                }

                @Override
                public void onEvent(ServerSentEvent event) {
                    if (!tube.cancelled()) {
                        tube.send(event);
                    }
                }

                @Override
                public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                    parsingHandle.set(context.parsingHandle());
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
            });
        });
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        SimpleHttpRequest apacheRequest = toSimpleApacheRequest(request);
        asyncClient.execute(apacheRequest, new FutureCallback<>() {
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

    private SuccessfulHttpResponse fromApacheResponse(ClassicHttpResponse httpResponse) {
        Map<String, List<String>> headers = new HashMap<>();
        org.apache.hc.core5.http.Header[] allHeaders = httpResponse.getHeaders();
        for (org.apache.hc.core5.http.Header header : allHeaders) {
            headers.computeIfAbsent(header.getName(), k -> new ArrayList<>()).add(header.getValue());
        }
        return SuccessfulHttpResponse.builder()
                .statusCode(httpResponse.getCode())
                .headers(headers)
                .body(readBody(httpResponse))
                .build();
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

        String body;
        if (contentType != null && contentType.contains("text/event-stream")) {
            body = null;
        } else {
            body = apacheResponse.getBodyText();
        }

        return SuccessfulHttpResponse.builder()
                .statusCode(apacheResponse.getCode())
                .headers(headers)
                .body(body)
                .build();
    }
}
