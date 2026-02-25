package dev.langchain4j.mcp.client.transport.http;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpHeadersSupplier;
import dev.langchain4j.mcp.client.logging.McpLoggers;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpInitializationNotification;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamableHttpMcpTransport implements McpTransport {
    private static final Logger LOG = LoggerFactory.getLogger(StreamableHttpMcpTransport.class);
    private static final long DEFAULT_SUBSIDIARY_RETRY_MS = 5000;
    private final String url;
    private final McpHeadersSupplier customHeadersSupplier;
    private final boolean logResponses;
    private final boolean logRequests;
    private final Logger trafficLog;
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final AtomicReference<CompletableFuture<JsonNode>> initializeInProgress = new AtomicReference<>(null);
    private volatile McpOperationHandler operationHandler;
    private final HttpClient httpClient;
    private final SSLContext sslContext;
    private final HttpClient.Version httpVersion;
    private McpInitializeRequest initializeRequest;
    private final AtomicReference<String> mcpSessionId = new AtomicReference<>();

    // Subsidiary SSE channel fields
    private final boolean subsidiaryChannelEnabled;
    private volatile Runnable onFailureCallback;
    private volatile boolean subsidiaryChannelEstablished;
    private final AtomicReference<String> subsidiaryLastEventId = new AtomicReference<>();
    private final AtomicLong subsidiaryRetryMs = new AtomicLong(DEFAULT_SUBSIDIARY_RETRY_MS);
    private final Executor executor;
    private AtomicBoolean closed = new AtomicBoolean(false);

    public StreamableHttpMcpTransport(StreamableHttpMcpTransport.Builder builder) {
        url = ensureNotNull(builder.url, "Missing server endpoint URL");
        logRequests = builder.logRequests;
        logResponses = builder.logResponses;
        trafficLog = getOrDefault(builder.logger, McpLoggers.traffic());
        Duration timeout = getOrDefault(builder.timeout, Duration.ofSeconds(60));
        customHeadersSupplier = getOrDefault(builder.customHeadersSupplier, (i) -> Map.of());
        sslContext = builder.sslContext;
        httpVersion = builder.forceHttpVersion1_1 ? HttpClient.Version.HTTP_1_1 : HttpClient.Version.HTTP_2;
        subsidiaryChannelEnabled = builder.subsidiaryChannelEnabled;
        executor = getOrDefault(builder.executor, DefaultExecutorProvider.getDefaultExecutorService());
        HttpClient.Builder clientBuilder =
                HttpClient.newBuilder().connectTimeout(timeout).version(httpVersion);
        if (builder.executor != null) {
            clientBuilder.executor(builder.executor);
        }
        if (sslContext != null) {
            clientBuilder.sslContext(sslContext);
        }
        httpClient = clientBuilder.build();
    }

    @Override
    public void start(McpOperationHandler operationHandler) {
        this.operationHandler = operationHandler;
    }

    @Override
    public CompletableFuture<JsonNode> initialize(McpInitializeRequest operation) {
        this.initializeRequest = operation;
        CompletableFuture<JsonNode> completableFuture = execute(new McpCallContext(null, operation), false);
        initializeInProgress.set(completableFuture);
        return completableFuture
                .thenCompose(originalResponse -> {
                    initializeInProgress.set(null);
                    return CompletableFuture.completedFuture(originalResponse);
                })
                .thenCompose(originalResponse -> execute(
                                new McpCallContext(null, new McpInitializationNotification()), false)
                        .thenCompose(nullNode -> CompletableFuture.completedFuture(originalResponse)))
                .thenCompose(originalResponse -> {
                    if (subsidiaryChannelEnabled) {
                        return startSubsidiaryChannel(true)
                                .thenCompose(v -> CompletableFuture.completedFuture(originalResponse));
                    }
                    return CompletableFuture.completedFuture(originalResponse);
                });
    }

    private HttpRequest createRequest(McpJsonRpcMessage message, McpCallContext callContext)
            throws JsonProcessingException {
        String body = OBJECT_MAPPER.writeValueAsString(message);
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(body);
        if (logRequests) {
            trafficLog.info("Request: {}", body);
        }
        final HttpRequest.Builder builder = HttpRequest.newBuilder();
        String sessionId = mcpSessionId.get();
        if (sessionId != null && !(message instanceof McpInitializeRequest)) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        Map<String, String> headers = customHeadersSupplier.apply(callContext);
        if (headers != null) {
            headers.forEach(builder::header);
        }
        return builder.uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json,text/event-stream")
                .POST(bodyPublisher)
                .build();
    }

    @Override
    public CompletableFuture<JsonNode> executeOperationWithResponse(McpClientMessage operation) {
        return executeOperationWithResponse(new McpCallContext(null, operation));
    }

    @Override
    public CompletableFuture<JsonNode> executeOperationWithResponse(McpCallContext context) {
        return execute(context, false);
    }

    @Override
    public void executeOperationWithoutResponse(McpClientMessage operation) {
        executeOperationWithoutResponse(new McpCallContext(null, operation));
    }

    @Override
    public void executeOperationWithoutResponse(McpCallContext context) {
        execute(context, false);
    }

    @Override
    public void checkHealth() {
        // no transport-specific checks right now
    }

    @Override
    public void onFailure(Runnable actionOnFailure) {
        this.onFailureCallback = actionOnFailure;
    }

    private CompletableFuture<JsonNode> execute(McpCallContext context, boolean isRetry) {
        Long id = context.message().getId();
        if (!(context.message() instanceof McpInitializeRequest)) {
            CompletableFuture<JsonNode> reinitializeInProgress = this.initializeInProgress.get();
            if (reinitializeInProgress != null) {
                reinitializeInProgress.join();
            }
        }
        HttpRequest request = null;
        try {
            request = createRequest(
                    context.message(), new McpCallContext(context.invocationContext(), context.message()));
        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(e);
        }
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        if (id != null) {
            operationHandler.startOperation(id, future);
        }

        httpClient
                .sendAsync(request, responseInfo -> {
                    if (!isExpectedStatusCode(responseInfo.statusCode())) {
                        if (!(context.message() instanceof McpInitializeRequest) && responseInfo.statusCode() == 404) {
                            if (!isRetry) {
                                initialize(StreamableHttpMcpTransport.this.initializeRequest)
                                        .thenAccept(node -> {
                                            execute(context, true)
                                                    .thenAccept(future::complete)
                                                    .exceptionally(t -> {
                                                        future.completeExceptionally(t);
                                                        return null;
                                                    });
                                        })
                                        .exceptionally(t -> {
                                            future.completeExceptionally(t);
                                            return null;
                                        });
                            }
                        } else {
                            future.completeExceptionally(
                                    new RuntimeException("Unexpected status code: " + responseInfo.statusCode()));
                        }
                        return HttpResponse.BodySubscribers.discarding();
                    } else {
                        Optional<String> contentType = responseInfo.headers().firstValue("Content-Type");
                        Optional<String> mcpSessionId = responseInfo.headers().firstValue("Mcp-Session-Id");
                        if (mcpSessionId.isPresent()) {
                            LOG.debug("Assigned MCP session ID: {}", mcpSessionId);
                            StreamableHttpMcpTransport.this.mcpSessionId.set(mcpSessionId.get());
                        }
                        if (id != null
                                && contentType.isPresent()
                                && contentType.get().contains("text/event-stream")) {
                            // the server has started an SSE stream
                            return HttpResponse.BodySubscribers.fromLineSubscriber(
                                    new SseSubscriber(future, logResponses, operationHandler, trafficLog));
                        } else {
                            // the server has returned a regular HTTP response
                            return HttpResponse.BodySubscribers.mapping(
                                    HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8), responseBody -> {
                                        if (logResponses) {
                                            trafficLog.info("Response: {}", responseBody);
                                        }
                                        if (id == null) {
                                            future.complete(null);
                                        }
                                        try {
                                            JsonNode node = OBJECT_MAPPER.readTree(responseBody);
                                            operationHandler.handle(node);
                                            return null;
                                        } catch (IOException e) {
                                            future.completeExceptionally(e);
                                            return null;
                                        }
                                    });
                        }
                    }
                })
                .exceptionally(t -> {
                    future.completeExceptionally(t);
                    return null;
                });
        return future;
    }

    /**
     * Opens the subsidiary SSE channel by issuing an HTTP GET to the MCP endpoint.
     * This allows the server to send notifications and requests to the client
     * without the client first sending data via HTTP POST.
     *
     * @param firstAttempt if true, failures will not trigger reconnection
     * @return a future that completes when the channel setup attempt finishes
     */
    private CompletableFuture<Void> startSubsidiaryChannel(boolean firstAttempt) {
        if (closed.get()) {
            return CompletableFuture.completedFuture(null);
        }
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "text/event-stream")
                .GET();
        String sessionId = mcpSessionId.get();
        if (sessionId != null) {
            requestBuilder.header("Mcp-Session-Id", sessionId);
        }
        String lastId = subsidiaryLastEventId.get();
        if (lastId != null) {
            requestBuilder.header("Last-Event-ID", lastId);
        }
        Map<String, String> headers = customHeadersSupplier.apply(null);
        if (headers != null) {
            headers.forEach(requestBuilder::header);
        }
        HttpRequest request = requestBuilder.build();

        CompletableFuture<Void> result = new CompletableFuture<>();
        SseSubscriber subscriber = new SseSubscriber(
                logResponses,
                operationHandler,
                trafficLog,
                subsidiaryLastEventId,
                subsidiaryRetryMs,
                this::scheduleSubsidiaryReconnect,
                closed);

        httpClient
                .sendAsync(request, responseInfo -> {
                    int statusCode = responseInfo.statusCode();
                    Optional<String> contentType = responseInfo.headers().firstValue("Content-Type");
                    if (isExpectedStatusCode(statusCode)
                            && contentType.isPresent()
                            && contentType.get().contains("text/event-stream")) {
                        subsidiaryChannelEstablished = true;
                        LOG.debug("Subsidiary SSE channel established");
                        result.complete(null);
                        return HttpResponse.BodySubscribers.fromLineSubscriber(subscriber);
                    } else {
                        if (firstAttempt) {
                            LOG.warn(
                                    "Failed to open subsidiary SSE channel (status={}, contentType={}), will not re-attempt",
                                    statusCode,
                                    contentType.orElse("absent"));
                        } else {
                            LOG.debug(
                                    "Failed to reconnect subsidiary SSE channel (status={}, contentType={}), scheduling retry",
                                    statusCode,
                                    contentType.orElse("absent"));
                            if (!closed.get()) {
                                scheduleSubsidiaryReconnect();
                            }
                        }
                        result.complete(null);
                        return HttpResponse.BodySubscribers.discarding();
                    }
                })
                .exceptionally(t -> {
                    if (!closed.get()) {
                        if (firstAttempt) {
                            LOG.warn("Failed to open subsidiary SSE channel", t);
                        } else {
                            LOG.debug("Subsidiary SSE channel connection failed, scheduling retry", t);
                            scheduleSubsidiaryReconnect();
                        }
                    }
                    result.complete(null);
                    return null;
                });
        return result;
    }

    private void scheduleSubsidiaryReconnect() {
        if (closed.get() || !subsidiaryChannelEstablished) {
            return;
        }
        long delayMs = subsidiaryRetryMs.get();
        LOG.debug("Scheduling subsidiary SSE channel reconnect in {} ms", delayMs);
        Executor delayedExecutor = CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS, executor);
        CompletableFuture.runAsync(
                () -> {
                    if (!closed.get()) {
                        startSubsidiaryChannel(false);
                    }
                },
                delayedExecutor);
    }

    private boolean isExpectedStatusCode(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    @Override
    public void close() throws IOException {
        closed.set(true);
        // The httpClient.close() method only exists on JDK 21+, so invoke it only if we can.
        // Replace this with a normal method call when switching the base to JDK 21+.
        try {
            httpClient.getClass().getMethod("close").invoke(httpClient);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Executor executor;
        private String url;
        private McpHeadersSupplier customHeadersSupplier;
        private Duration timeout;
        private boolean logRequests = false;
        private boolean logResponses = false;
        private Logger logger;
        private SSLContext sslContext;
        private boolean forceHttpVersion1_1;
        private boolean subsidiaryChannelEnabled = false;

        /**
         * The URL of the MCP server.
         */
        public StreamableHttpMcpTransport.Builder url(String url) {
            this.url = url;
            return this;
        }

        /**
         * The request headers of the MCP server.
         */
        public StreamableHttpMcpTransport.Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = (i) -> customHeaders;
            return this;
        }

        /**
         * A supplier for dynamic request headers of the MCP server.
         * The supplier is called for each request, allowing headers to be updated dynamically.
         */
        public StreamableHttpMcpTransport.Builder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = i -> customHeadersSupplier.get();
            return this;
        }

        /**
         * A supplier for dynamic request headers of the MCP server.
         * The supplier is called for each request, allowing headers to be updated dynamically.
         */
        public StreamableHttpMcpTransport.Builder customHeaders(McpHeadersSupplier customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        /**
         * The connection timeout (applied on the http client level). Application-level
         * timeouts are handled by the MCP client itself.
         */
        public StreamableHttpMcpTransport.Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Whether to log all requests that are sent over this transport.
         */
        public StreamableHttpMcpTransport.Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Whether to log all responses received over this transport.
         */
        public StreamableHttpMcpTransport.Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * Sets a custom {@link Logger} to be used for traffic logging (both requests and responses).
         * This logger will be used for both regular HTTP responses and Server-Sent Events (SSE) traffic.
         * If not specified, a default logger will be used.
         *
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for traffic logging.
         * @return {@code this}.
         */
        public StreamableHttpMcpTransport.Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * An optional {@link Executor} that will be used for executing requests and handling responses.
         * It will also be used for scheduling auto-reconnect attempts of the subsidiary SSE channel if that is enabled.
         * If not provided, a default shared executor will be used.
         */
        public StreamableHttpMcpTransport.Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Supplies a custom {@link SSLContext} used when establishing HTTPS connections to the MCP server,
         * allowing private CAs or certificates.
         */
        public StreamableHttpMcpTransport.Builder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        /**
         * Forces the transport to use HTTP/1.1 instead of the default HTTP/2.
         */
        public StreamableHttpMcpTransport.Builder setHttpVersion1_1() {
            this.forceHttpVersion1_1 = true;
            return this;
        }

        /**
         * Enables or disables the subsidiary SSE channel. When enabled, the transport
         * will open an HTTP GET-based SSE stream after initialization, allowing the
         * server to send notifications and requests to the client without the client
         * first sending data via HTTP POST. If the server does not support the
         * subsidiary channel (returns 405), the transport will log a warning and
         * continue without it. If the stream breaks after being successfully
         * established, the transport will automatically attempt to reconnect.
         * Defaults to {@code false}.
         */
        public StreamableHttpMcpTransport.Builder subsidiaryChannel(boolean subsidiaryChannelEnabled) {
            this.subsidiaryChannelEnabled = subsidiaryChannelEnabled;
            return this;
        }

        public StreamableHttpMcpTransport build() {
            return new StreamableHttpMcpTransport(this);
        }
    }
}
