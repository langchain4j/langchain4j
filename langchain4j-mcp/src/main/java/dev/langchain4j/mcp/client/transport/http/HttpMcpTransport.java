package dev.langchain4j.mcp.client.transport.http;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.protocol.CancellationNotification;
import dev.langchain4j.mcp.client.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.client.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.client.protocol.McpListToolsRequest;
import dev.langchain4j.mcp.client.transport.McpTransport;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpMcpTransport implements McpTransport {

    private static final Logger log = LoggerFactory.getLogger(HttpMcpTransport.class);
    private final String sseUrl;
    private final OkHttpClient client;
    private final boolean logResponses;
    private final boolean logRequests;
    private EventSource mcpSseEventListener;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Map<Long, CompletableFuture<JsonNode>> pendingOperations = new ConcurrentHashMap<>();

    // this is obtained from the server after initializing the SSE channel
    private volatile String postUrl;

    public HttpMcpTransport(Builder builder) {
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
        Duration timeout = getOrDefault(builder.timeout, Duration.ofSeconds(60));
        httpClientBuilder.callTimeout(timeout);
        httpClientBuilder.connectTimeout(timeout);
        httpClientBuilder.readTimeout(timeout);
        httpClientBuilder.writeTimeout(timeout);
        this.logRequests = builder.logRequests;
        if (builder.logRequests) {
            httpClientBuilder.addInterceptor(new McpRequestLoggingInterceptor());
        }
        this.logResponses = builder.logResponses;
        sseUrl = ensureNotNull(builder.sseUrl, "Missing SSE endpoint URL");
        client = httpClientBuilder.build();
    }

    @Override
    public void start() {
        mcpSseEventListener = startSseChannel(logResponses);
    }

    @Override
    public JsonNode initialize(final McpInitializeRequest request) {
        Request httpRequest = null;
        try {
            httpRequest = new Request.Builder()
                    .url(postUrl)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(OBJECT_MAPPER.writeValueAsBytes(request)))
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return executeAndWait(httpRequest, request.getId());
    }

    @Override
    public JsonNode listTools(McpListToolsRequest operation) {
        try {
            Request httpRequest = new Request.Builder()
                    .url(postUrl)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(OBJECT_MAPPER.writeValueAsBytes(operation)))
                    .build();
            return executeAndWait(httpRequest, operation.getId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JsonNode executeTool(
            McpCallToolRequest operation,
            Duration timeout,
            Supplier<CancellationNotification> cancellationNotificationSupplier)
            throws TimeoutException {
        try {
            Request httpRequest = new Request.Builder()
                    .url(postUrl)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(OBJECT_MAPPER.writeValueAsBytes(operation)))
                    .build();
            return executeAndWait(httpRequest, operation.getId(), timeout, cancellationNotificationSupplier);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode executeAndWait(Request request, Long id) {
        try {
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            pendingOperations.put(id, future);
            try (final Response response = client.newCall(request).execute()) {
                int statusCode = response.code();
                if (statusCode < 200 || statusCode >= 300) {
                    throw new RuntimeException("Unexpected status code: " + statusCode);
                }
            }
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(id);
        }
    }

    private JsonNode executeAndWait(
            Request request,
            Long id,
            Duration timeout,
            Supplier<CancellationNotification> cancellationNotificationSupplier)
            throws TimeoutException {
        try {
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            pendingOperations.put(id, future);
            try (final Response response = client.newCall(request).execute()) {
                int statusCode = response.code();
                if (statusCode < 200 || statusCode >= 300) {
                    throw new RuntimeException("Unexpected status code: " + statusCode);
                }
            }
            long timeoutMillis = timeout.toMillis() == 0 ? Long.MAX_VALUE : timeout.toMillis();
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutException) {
            if (cancellationNotificationSupplier != null) {
                CancellationNotification cancellationNotification = cancellationNotificationSupplier.get();
                try {
                    Request cancellationRequest = new Request.Builder()
                            .url(postUrl)
                            .header("Content-Type", "application/json")
                            .post(RequestBody.create(OBJECT_MAPPER.writeValueAsBytes(cancellationNotification)))
                            .build();
                    try (Response response = client.newCall(cancellationRequest).execute()) {}
                } catch (IOException e) {
                    log.warn("Failed to send cancellation notification", e);
                }
            }
            throw timeoutException;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(id);
        }
    }

    private EventSource startSseChannel(boolean logResponses) {
        Request request = new Request.Builder().url(sseUrl).build();
        CompletableFuture<String> initializationFinished = new CompletableFuture<>();
        SseEventListener listener = new SseEventListener(pendingOperations, logResponses, initializationFinished);
        EventSource eventSource = EventSources.createFactory(client).newEventSource(request, listener);
        // wait for the SSE channel to be created, receive the POST url from the server, throw an exception if that
        // failed
        try {
            int timeout = client.callTimeoutMillis() > 0 ? client.callTimeoutMillis() : Integer.MAX_VALUE;
            String relativePostUrl = initializationFinished.get(timeout, TimeUnit.MILLISECONDS);
            postUrl = buildAbsolutePostUrl(relativePostUrl);
            log.debug("Received the server's POST URL: " + postUrl);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return eventSource;
    }

    private String buildAbsolutePostUrl(String relativePostUrl) {
        try {
            return URI.create(this.sseUrl).resolve(relativePostUrl).toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        if (mcpSseEventListener != null) {
            mcpSseEventListener.cancel();
        }
        if (client != null) {
            client.dispatcher().executorService().shutdown();
        }
    }

    public static class Builder {

        private String sseUrl;
        private Duration timeout;
        private boolean logRequests = false;
        private boolean logResponses = false;

        /**
         * The initial URL where to connect to the server and request a SSE
         * channel.
         */
        public Builder sseUrl(String sseUrl) {
            this.sseUrl = sseUrl;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public HttpMcpTransport build() {
            return new HttpMcpTransport(this);
        }
    }
}
