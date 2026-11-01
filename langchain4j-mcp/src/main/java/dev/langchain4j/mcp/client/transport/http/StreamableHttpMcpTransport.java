package dev.langchain4j.mcp.client.transport.http;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.sse.HttpErrorResponseReceived;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpHeadersSupplier;
import dev.langchain4j.mcp.client.logging.McpLoggers;
import dev.langchain4j.mcp.client.transport.McpHeaderEncoding;
import dev.langchain4j.mcp.client.transport.McpJson;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpInitializationNotification;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
import dev.langchain4j.spi.CapturedContext;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
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
    private final AtomicReference<CompletableFuture<String>> initializeInProgress = new AtomicReference<>(null);
    private volatile McpOperationHandler operationHandler;
    private final HttpClient httpClient;
    private final AutoCloseable ownedHttpClient;
    private final SSLContext sslContext;
    // Legacy protocol only (up to 2025-11-25) — stored for 404 reinitialize
    private volatile Initialization initialization;
    private final AtomicReference<String> mcpSessionId = new AtomicReference<>();
    private volatile boolean modernProtocol;
    private volatile String protocolVersionHeader;

    // Subsidiary SSE channel fields
    private final boolean subsidiaryChannelEnabled;
    private volatile Runnable onFailureCallback;
    private volatile boolean subsidiaryChannelEstablished;
    private final AtomicReference<String> subsidiaryLastEventId = new AtomicReference<>();
    private final AtomicLong subsidiaryRetryMs = new AtomicLong(DEFAULT_SUBSIDIARY_RETRY_MS);
    private final Executor executor;
    private final Executor responseExecutor;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Set<HttpExchange> activeExchanges = ConcurrentHashMap.newKeySet();

    public StreamableHttpMcpTransport(StreamableHttpMcpTransport.Builder builder) {
        url = ensureNotNull(builder.url, "Missing server endpoint URL");
        logRequests = builder.logRequests;
        logResponses = builder.logResponses;
        trafficLog = getOrDefault(builder.logger, McpLoggers.traffic());
        Duration timeout = getOrDefault(builder.timeout, Duration.ofSeconds(60));
        customHeadersSupplier = getOrDefault(builder.customHeadersSupplier, (i) -> Map.of());
        sslContext = builder.sslContext;
        subsidiaryChannelEnabled = builder.subsidiaryChannelEnabled;
        executor = getOrDefault(builder.executor, DefaultExecutorProvider.getDefaultExecutor());
        responseExecutor = builder.executor;
        if (builder.httpClientBuilder != null && builder.httpClient != null) {
            throw new IllegalArgumentException("Configure either httpClientBuilder or httpClient, not both");
        }
        if (builder.httpClientBuilder != null || builder.httpClient != null) {
            String supplied = builder.httpClient != null ? "httpClient(...)" : "httpClientBuilder(...)";
            List<String> conflicting = new ArrayList<>();
            if (sslContext != null) {
                conflicting.add("sslContext(...)");
            }
            if (builder.forceHttpVersion1_1) {
                conflicting.add("setHttpVersion1_1()");
            }
            if (builder.followRedirects) {
                conflicting.add("followRedirects(true)");
            }
            if (builder.httpClient != null && builder.timeout != null) {
                conflicting.add("timeout(...)");
            }
            if (!conflicting.isEmpty()) {
                throw new IllegalArgumentException(String.join(", ", conflicting) + " cannot be combined with "
                        + supplied + "; configure it on the HTTP client instead");
            }
            if (builder.httpClient != null) {
                httpClient = builder.httpClient;
                ownedHttpClient = null;
            } else {
                HttpClientBuilder clientBuilder = builder.httpClientBuilder;
                if (builder.timeout != null) {
                    clientBuilder.connectTimeout(builder.timeout);
                }
                httpClient = ensureNotNull(clientBuilder.build(), "httpClientBuilder.build()");
                ownedHttpClient = httpClient instanceof AutoCloseable closeable ? closeable : null;
            }
        } else {
            java.net.http.HttpClient.Builder clientBuilder = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(timeout)
                    .version(
                            builder.forceHttpVersion1_1
                                    ? java.net.http.HttpClient.Version.HTTP_1_1
                                    : java.net.http.HttpClient.Version.HTTP_2);
            if (builder.followRedirects) {
                clientBuilder.followRedirects(java.net.http.HttpClient.Redirect.NORMAL);
            }
            if (builder.executor != null) {
                clientBuilder.executor(builder.executor);
            }
            if (sslContext != null) {
                clientBuilder.sslContext(sslContext);
            }
            JdkHttpClient client = JdkHttpClient.builder()
                    .httpClientBuilder(clientBuilder)
                    .streamingBackpressure(true)
                    .build();
            ownedHttpClient = client;
            httpClient = client;
        }
    }

    @Override
    public void start(McpOperationHandler operationHandler) {
        this.operationHandler = operationHandler;
    }

    @Override
    public CompletableFuture<String> sendInitializeRequest(McpInitializeRequest operation) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("MCP transport is closed"));
        }
        try {
            Initialization captured = new Initialization(operation, captureRequestContext());
            initialization = captured;
            return initializeSession(captured);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<String> initializeSession(Initialization captured) {
        CompletableFuture<String> completableFuture =
                execute(new McpCallContext(null, captured.request()), false, true, captured.context());
        initializeInProgress.set(completableFuture);
        return completableFuture
                .whenComplete((response, error) -> initializeInProgress.compareAndSet(completableFuture, null))
                .thenCompose(originalResponse -> execute(
                                new McpCallContext(null, new McpInitializationNotification()),
                                false,
                                false,
                                captured.context())
                        .thenApply(ignored -> originalResponse))
                .thenCompose(originalResponse -> {
                    if (subsidiaryChannelEnabled) {
                        return startSubsidiaryChannel(true, captured.context()).thenApply(ignored -> originalResponse);
                    }
                    return CompletableFuture.completedFuture(originalResponse);
                });
    }

    private HttpRequest createRequest(
            McpJsonRpcMessage message, McpCallContext callContext, Map<String, String> customHeaders) {
        String body = McpJson.serialize(message);
        if (logRequests) {
            trafficLog.info("Request: {}", body);
        }
        final HttpRequest.Builder builder = HttpRequest.builder();

        if (modernProtocol) {
            // Modern protocol headers
            if (protocolVersionHeader != null) {
                builder.addHeader("MCP-Protocol-Version", protocolVersionHeader);
            }
            // Extract method from JSON-RPC message
            Map<String, Object> bodyMap = McpJson.toMap(body);
            String method = text(bodyMap, "method");
            if (method != null) {
                builder.addHeader("Mcp-Method", method);
            }
            // Extract name for tools/call, resources/read, prompts/get
            Map<?, ?> params = bodyMap.get("params") instanceof Map<?, ?> map ? map : Map.of();
            if ("tools/call".equals(method) || "prompts/get".equals(method)) {
                String name = text(params, "name");
                if (name != null) {
                    builder.addHeader("Mcp-Name", McpHeaderEncoding.encode(name));
                }
            } else if ("resources/read".equals(method)) {
                String uri = text(params, "uri");
                if (uri != null) {
                    builder.addHeader("Mcp-Name", McpHeaderEncoding.encode(uri));
                }
            }
            // Mcp-Param headers from context
            if (callContext.mcpParamHeaders() != null) {
                callContext.mcpParamHeaders().forEach((k, v) -> builder.addHeader("Mcp-Param-" + k, v));
            }
        } else {
            // Legacy: send Mcp-Session-Id
            String sessionId = mcpSessionId.get();
            if (sessionId != null && !(message instanceof McpInitializeRequest)) {
                builder.addHeader("Mcp-Session-Id", sessionId);
            }
        }

        customHeaders.forEach(builder::addHeader);
        return builder.url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json,text/event-stream")
                .method(HttpMethod.POST)
                .body(body)
                .build();
    }

    private static String text(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    @Override
    public CompletableFuture<String> sendRequest(McpCallContext context) {
        return execute(context, false);
    }

    @Override
    public void sendMessage(McpCallContext context) {
        execute(context, false, false);
    }

    @Override
    public void checkHealth() {
        // no transport-specific checks right now
    }

    @Override
    public void onFailure(Runnable actionOnFailure) {
        this.onFailureCallback = actionOnFailure;
    }

    /**
     * Returns the MCP session ID assigned by the server, or {@code null} if no session
     * has been established yet (or the server does not use sessions). The session ID is
     * captured from the {@code Mcp-Session-Id} response header during initialization
     * and reused on subsequent requests via the same header.
     * This only applies to the legacy protocol (before 2026-07-28).
     */
    public String getMcpSessionId() {
        return mcpSessionId.get();
    }

    /**
     * Sets the MCP session ID to be sent on subsequent requests via the
     * {@code Mcp-Session-Id} header. This is intended for scenarios where a session
     * obtained elsewhere (for example, in another process or pod) needs to be resumed
     * by this transport, allowing stateless deployments without sticky sessions.
     * This only applies to the legacy protocol (before 2026-07-28).
     */
    public void setMcpSessionId(String mcpSessionId) {
        if (modernProtocol) {
            LOG.warn(
                    "Setting MCP session ID has no effect in modern (2026-07-28+) protocol mode, which does not use sessions.");
        }
        this.mcpSessionId.set(mcpSessionId);
    }

    /**
     * Enables or disables modern protocol mode. When enabled, the transport sends
     * modern protocol headers ({@code MCP-Protocol-Version}, {@code Mcp-Method},
     * {@code Mcp-Name}) and skips legacy session management ({@code Mcp-Session-Id}
     * header and 404-triggered reinitialization).
     */
    public void setModernProtocol(boolean modernProtocol) {
        this.modernProtocol = modernProtocol;
    }

    /**
     * Sets the protocol version to be sent in the {@code MCP-Protocol-Version} header
     * when modern protocol mode is enabled.
     */
    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersionHeader = protocolVersion;
    }

    private CompletableFuture<String> execute(McpCallContext context, boolean isRetry) {
        return execute(context, isRetry, true);
    }

    /**
     * @param expectResponse whether this message expects a response from the server. A notification
     *     or a response to a server-initiated request does not, and must not be registered as a
     *     pending operation: its id comes from the server's id space, so registering it would both
     *     leave a future that is never completed and displace a client-initiated request that is
     *     still in flight and happens to carry the same id.
     */
    private CompletableFuture<String> execute(McpCallContext context, boolean isRetry, boolean expectResponse) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("MCP transport is closed"));
        }
        try {
            return execute(context, isRetry, expectResponse, captureRequestContext());
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CapturedContext captureRequestContext() {
        return getOrDefault(DefaultExecutorProvider.captureContext(), CapturedContext.NONE);
    }

    private static void runInContext(CapturedContext context, CompletableFuture<?> result, Runnable action) {
        try {
            context.wrap(action).run();
        } catch (RuntimeException e) {
            result.completeExceptionally(e);
        }
    }

    private CompletableFuture<String> execute(
            McpCallContext context, boolean isRetry, boolean expectResponse, CapturedContext requestContext) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("MCP transport is closed"));
        }
        CompletableFuture<String> result = new CompletableFuture<>();
        runInContext(requestContext, result, () -> {
            // Capture caller-derived headers before deferring to the initialization completion thread.
            // Session and protocol headers are still selected when the request is sent.
            Map<String, String> supplied = customHeadersSupplier.apply(context);
            Map<String, String> headers = supplied == null ? Map.of() : Map.copyOf(supplied);
            CompletableFuture<String> pendingInitialization =
                    context.message() instanceof McpInitializeRequest ? null : initializeInProgress.get();
            if (pendingInitialization == null) {
                executeRequest(context, isRetry, expectResponse, headers, requestContext, result);
            } else {
                deferRequest(pendingInitialization, context, isRetry, expectResponse, headers, requestContext, result);
            }
        });
        return result;
    }

    private void deferRequest(
            CompletableFuture<String> pendingInitialization,
            McpCallContext context,
            boolean isRetry,
            boolean expectResponse,
            Map<String, String> headers,
            CapturedContext requestContext,
            CompletableFuture<String> result) {
        AtomicReference<CapturedContext> pendingContext = new AtomicReference<>(requestContext);
        result.whenComplete((value, error) -> pendingContext.set(null));
        pendingInitialization.whenComplete((ignored, error) -> {
            CapturedContext captured = pendingContext.getAndSet(null);
            if (captured == null || result.isDone()) {
                return;
            }
            runInContext(captured, result, () -> {
                if (error != null) {
                    result.completeExceptionally(error);
                } else {
                    executeRequest(context, isRetry, expectResponse, headers, captured, result);
                }
            });
        });
    }

    private void executeRequest(
            McpCallContext context,
            boolean isRetry,
            boolean expectResponse,
            Map<String, String> headers,
            CapturedContext requestContext,
            CompletableFuture<String> future) {
        if (closed.get()) {
            future.completeExceptionally(new IllegalStateException("MCP transport is closed"));
            return;
        }
        if (future.isDone()) {
            return;
        }
        HttpRequest request = createRequest(context.message(), context, headers);
        if (expectResponse && context.message().getId() != null) {
            operationHandler.expectResponse(context.message().getId(), future);
        }
        HttpExchange exchange = new HttpExchange(context, isRetry, expectResponse, future, requestContext);
        send(request, exchange);
    }

    private static void forwardResult(CompletableFuture<String> source, CompletableFuture<String> target) {
        target.whenComplete((result, error) -> {
            if (error != null) {
                source.cancel(true);
            }
        });
        source.whenComplete((result, error) -> {
            if (error == null) {
                target.complete(result);
            } else {
                target.completeExceptionally(error);
            }
        });
    }

    private void send(HttpRequest request, HttpExchange exchange) {
        activeExchanges.add(exchange);
        if (closed.get() || exchange.finished.get()) {
            exchange.cancel();
            activeExchanges.remove(exchange);
            return;
        }
        try {
            Flow.Subscriber<HttpStreamingEvent> subscriber =
                    responseExecutor == null ? exchange : new McpResponseSubscriber(exchange, responseExecutor);
            httpClient.stream(request, new McpResponseParser()).subscribe(subscriber);
        } catch (RuntimeException e) {
            exchange.onError(e);
        }
    }

    /**
     * Opens the subsidiary SSE channel by issuing an HTTP GET to the MCP endpoint.
     * This allows the server to send notifications and requests to the client
     * without the client first sending data via HTTP POST.
     * Only used with the legacy MCP protocol (versions up to 2025-11-25).
     * In modern protocol, notifications are received via {@code subscriptions/listen}.
     *
     * @param firstAttempt if true, failures will not trigger reconnection
     * @return a future that completes when the channel setup attempt finishes
     */
    private CompletableFuture<Void> startSubsidiaryChannel(boolean firstAttempt, CapturedContext requestContext) {
        if (closed.get()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        HttpExchange exchange = new HttpExchange(firstAttempt, result, requestContext);
        exchange.runInContext(() -> {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.builder()
                        .url(url)
                        .addHeader("Accept", "text/event-stream")
                        .method(HttpMethod.GET);
                String sessionId = mcpSessionId.get();
                if (sessionId != null) {
                    requestBuilder.addHeader("Mcp-Session-Id", sessionId);
                }
                String lastId = subsidiaryLastEventId.get();
                if (lastId != null) {
                    requestBuilder.addHeader("Last-Event-ID", lastId);
                }
                Map<String, String> headers = customHeadersSupplier.apply(null);
                if (headers != null) {
                    headers.forEach(requestBuilder::addHeader);
                }
                send(requestBuilder.build(), exchange);
            } catch (RuntimeException e) {
                exchange.handleError(e);
            }
        });
        return result;
    }

    private void scheduleSubsidiaryReconnect(CapturedContext requestContext) {
        if (closed.get() || !subsidiaryChannelEstablished) {
            return;
        }
        long delayMs = subsidiaryRetryMs.get();
        LOG.debug("Scheduling subsidiary SSE channel reconnect in {} ms", delayMs);
        Executor delayedExecutor = CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS, executor);
        CompletableFuture.runAsync(
                () -> {
                    if (!closed.get()) {
                        startSubsidiaryChannel(false, requestContext);
                    }
                },
                delayedExecutor);
    }

    @Override
    public boolean requiresCancellationNotification() {
        return false;
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            activeExchanges.forEach(HttpExchange::cancel);
            initialization = null;
            if (ownedHttpClient != null) {
                try {
                    ownedHttpClient.close();
                } catch (IOException e) {
                    throw e;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while closing the MCP HTTP client", e);
                } catch (Exception e) {
                    throw new IOException("Failed to close the MCP HTTP client", e);
                }
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private record Initialization(McpInitializeRequest request, CapturedContext context) {}

    /**
     * One HTTP exchange. The HTTP SPI owns byte delivery and cancellation; MCP retains response
     * dispatch, session recovery and the lifetime of the subsidiary channel.
     */
    private final class HttpExchange implements Flow.Subscriber<HttpStreamingEvent> {

        private final McpCallContext context;
        private final boolean retry;
        private final boolean expectResponse;
        private final CompletableFuture<String> future;
        private final boolean firstAttempt;
        private final CompletableFuture<Void> opened;
        private final CapturedContext requestContext;
        private final AtomicBoolean finished = new AtomicBoolean();
        private final StringBuilder body = new StringBuilder();
        private volatile Flow.Subscription subscription;
        private boolean sseResponse;
        private boolean responseReceived;

        private HttpExchange(
                McpCallContext context,
                boolean retry,
                boolean expectResponse,
                CompletableFuture<String> future,
                CapturedContext requestContext) {
            this.context = context;
            this.retry = retry;
            this.expectResponse = expectResponse;
            this.future = future;
            this.firstAttempt = false;
            this.opened = null;
            this.requestContext = requestContext;
            future.whenComplete((result, error) -> {
                if (error != null) {
                    cancel();
                }
            });
        }

        private HttpExchange(boolean firstAttempt, CompletableFuture<Void> opened, CapturedContext requestContext) {
            this.context = null;
            this.retry = false;
            this.expectResponse = false;
            this.future = null;
            this.firstAttempt = firstAttempt;
            this.opened = opened;
            this.requestContext = requestContext;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            runInContext(() -> {
                if (finished.get() || closed.get()) {
                    cancel();
                    subscription.cancel();
                } else {
                    subscription.request(Long.MAX_VALUE);
                }
            });
        }

        @Override
        public void onNext(HttpStreamingEvent event) {
            if (finished.get() || closed.get()) {
                return;
            }
            runInContext(() -> handleNext(event));
        }

        private void handleNext(HttpStreamingEvent event) {
            try {
                if (event instanceof HttpResponseReceived received) {
                    responseReceived = true;
                    onResponse(received.response());
                } else if (event instanceof HttpErrorResponseReceived errorResponse) {
                    handleError(new HttpException(
                            errorResponse.statusCode(), "Unexpected status code: " + errorResponse.statusCode()));
                } else if (event instanceof McpResponseParser.Line line) {
                    if (sseResponse) {
                        handleSseLine(line.data());
                    } else {
                        body.append(line.data()).append(line.ending());
                    }
                }
            } catch (RuntimeException e) {
                onError(e);
            }
        }

        private void onResponse(SuccessfulHttpResponse response) {
            boolean eventStream =
                    response.contentType() != null && response.contentType().contains("text/event-stream");
            if (opened != null) {
                if (!eventStream) {
                    onError(new IllegalStateException("Subsidiary channel did not return text/event-stream"));
                    return;
                }
                subsidiaryChannelEstablished = true;
                sseResponse = true;
            } else {
                if (!modernProtocol) {
                    response.headers().getOrDefault("Mcp-Session-Id", List.of()).stream()
                            .findFirst()
                            .ifPresent(mcpSessionId::set);
                }
                sseResponse = expectResponse && context.message().getId() != null && eventStream;
            }
            if (opened != null) {
                opened.complete(null);
            }
        }

        private void handleSseLine(String line) {
            if (future != null && future.isCancelled()) {
                return;
            }
            if (logResponses && !line.trim().isEmpty()) {
                trafficLog.info("SSE event received: " + line);
            }
            if (line.startsWith("data:")) {
                try {
                    operationHandler.onMessage(line.substring(5));
                } catch (RuntimeException e) {
                    trafficLog.warn("Failed to handle SSE event: " + line, e);
                }
            } else if (opened != null && line.startsWith("id:")) {
                subsidiaryLastEventId.set(line.substring(3).trim());
            } else if (opened != null && line.startsWith("retry:")) {
                try {
                    subsidiaryRetryMs.set(Long.parseLong(line.substring(6).trim()));
                } catch (NumberFormatException e) {
                    trafficLog.warn("Failed to parse SSE retry value: " + line, e);
                }
            }
        }

        @Override
        public void onError(Throwable error) {
            if (!finished.get()) {
                runInContext(() -> handleError(error));
            }
        }

        private void handleError(Throwable error) {
            if (!finish()) {
                return;
            }
            if (error instanceof HttpException httpError) {
                error = new HttpException(httpError.statusCode(), "Unexpected status code: " + httpError.statusCode());
            }
            cancelSubscription();
            if (closed.get()) {
                completeCancellation();
            } else if (opened != null) {
                if (firstAttempt && !sseResponse) {
                    if (error instanceof HttpException httpError) {
                        LOG.warn(
                                "Failed to open subsidiary SSE channel (status={}), will not re-attempt",
                                httpError.statusCode());
                    } else {
                        LOG.warn("Failed to open subsidiary SSE channel", error);
                    }
                } else {
                    LOG.debug("Subsidiary SSE channel failed, scheduling retry", error);
                    scheduleSubsidiaryReconnect(requestContext);
                }
                opened.complete(null);
            } else if (error instanceof HttpException httpError
                    && httpError.statusCode() == 404
                    && !(context.message() instanceof McpInitializeRequest)
                    && !modernProtocol) {
                if (retry) {
                    future.completeExceptionally(new HttpException(
                            404, "Session expired again after reinitialization: server returned status code 404"));
                } else {
                    Initialization captured = initialization;
                    if (captured == null) {
                        future.completeExceptionally(new IllegalStateException("No initialization request available"));
                        return;
                    }
                    initializeSession(captured).whenComplete((ignored, initializationError) -> {
                        if (!future.isDone()) {
                            runInContext(() -> {
                                if (future.isDone()) {
                                    return;
                                }
                                if (initializationError != null) {
                                    future.completeExceptionally(initializationError);
                                } else {
                                    forwardResult(execute(context, true, expectResponse, requestContext), future);
                                }
                            });
                        }
                    });
                }
            } else {
                future.completeExceptionally(error);
            }
        }

        @Override
        public void onComplete() {
            if (!finished.get()) {
                runInContext(this::handleComplete);
            }
        }

        private void handleComplete() {
            if (!finish()) {
                return;
            }
            if (closed.get()) {
                completeCancellation();
            } else if (opened != null) {
                if (sseResponse) {
                    trafficLog.debug("Subsidiary SSE channel closed");
                    scheduleSubsidiaryReconnect(requestContext);
                }
                opened.complete(null);
            } else if (!responseReceived) {
                future.completeExceptionally(new IllegalStateException("HTTP stream completed without a response"));
            } else if (sseResponse) {
                trafficLog.debug("SSE channel closed");
            } else {
                String responseBody = body.toString();
                if (logResponses) {
                    trafficLog.info("Response: {}", responseBody);
                }
                if (!expectResponse || context.message().getId() == null) {
                    future.complete(null);
                }
                if (!responseBody.isBlank()) {
                    try {
                        operationHandler.onMessage(responseBody);
                    } catch (RuntimeException e) {
                        future.completeExceptionally(e);
                    }
                } else if (expectResponse && context.message().getId() != null) {
                    future.completeExceptionally(new IllegalStateException("Empty response to an MCP request"));
                }
            }
        }

        private boolean finish() {
            if (!finished.compareAndSet(false, true)) {
                return false;
            }
            activeExchanges.remove(this);
            return true;
        }

        private void cancel() {
            if (!finished.get()) {
                runInContext(this::cancelInContext);
            }
        }

        private void cancelInContext() {
            if (finish()) {
                cancelSubscription();
                completeCancellation();
            }
        }

        private void runInContext(Runnable action) {
            try {
                requestContext.wrap(action).run();
            } catch (RuntimeException e) {
                // Do not enter a failing context again while terminating the exchange.
                finish();
                cancelSubscription();
                if (future != null) {
                    future.completeExceptionally(e);
                } else {
                    opened.completeExceptionally(e);
                }
            }
        }

        private void cancelSubscription() {
            Flow.Subscription current = subscription;
            if (current != null) {
                current.cancel();
            }
        }

        private void completeCancellation() {
            if (future != null) {
                future.cancel(false);
            } else {
                opened.cancel(false);
            }
        }
    }

    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private HttpClient httpClient;
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
        private boolean followRedirects = false;

        /**
         * Supplies the HTTP backend used for all requests, including the subsidiary SSE channel.
         * The client must implement {@link HttpClient#stream(HttpRequest, ServerSentEventParser)}
         * and honor subscription cancellation; otherwise every request fails.
         * Without a custom builder, the transport uses the JDK HTTP client.
         * <p>
         * The transport owns the client it builds and closes it on {@link StreamableHttpMcpTransport#close()} if it implements
         * {@link AutoCloseable}; clients that do not implement it are not closed.
         * Use {@link #httpClient(HttpClient)} to supply a caller-owned, shared client instead.
         * Configure TLS, HTTP version and redirects on the supplied builder instead of using the
         * corresponding transport options. Cannot be combined with {@code httpClient}.
         * An explicit transport {@link #timeout(Duration)} is set as the connection timeout on the supplied builder;
         * otherwise the supplied backend retains its connection timeout settings and defaults.
         * The 60-second default applies only to the transport's default JDK backend.
         * <p>
         * The transport does not override the backend's read timeout, and MCP responses and SSE streams
         * can stay idle for a long time. Some backends have a short default read timeout (10 seconds for OkHttp),
         * so disable or raise it. Some backends follow redirects by default (OkHttp, Apache), which can send
         * custom headers and the request body to another host; disable redirects on the backend if not needed.
         *
         * @param httpClientBuilder the HTTP client builder
         * @return this builder
         * @since 1.23.0
         */
        @Experimental
        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = ensureNotNull(httpClientBuilder, "httpClientBuilder");
            return this;
        }

        /**
         * Supplies a caller-owned HTTP client, which may be shared by several transports.
         * The client must support the same streaming API as {@link #httpClientBuilder(HttpClientBuilder)}.
         * Closing this transport cancels its exchanges but does not close this client.
         * Configure connection timeout, TLS, HTTP version and redirects before supplying the client;
         * the corresponding transport options cannot be combined with this method.
         * Cannot be combined with {@code httpClientBuilder}.
         * The backend's read timeout and redirect settings apply, see {@link #httpClientBuilder(HttpClientBuilder)}.
         *
         * @param httpClient the HTTP client
         * @return this builder
         * @since 1.23.0
         */
        @Experimental
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = ensureNotNull(httpClient, "httpClient");
            return this;
        }

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
         * Cannot be combined with {@link #httpClient(HttpClient)}; configure it on the HTTP client instead.
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
         * Responses are handled on this executor also with {@link #httpClientBuilder(HttpClientBuilder)} or
         * {@link #httpClient(HttpClient)}; the HTTP client itself still sends requests on its own threads.
         * If not provided, a default shared executor is used for reconnects and responses are handled
         * on the HTTP client's threads.
         */
        public StreamableHttpMcpTransport.Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Supplies a custom {@link SSLContext} used when establishing HTTPS connections to the MCP server,
         * allowing private CAs or certificates.
         * Cannot be combined with {@link #httpClientBuilder(HttpClientBuilder)} or {@link #httpClient(HttpClient)};
         * configure it on the HTTP client instead.
         */
        public StreamableHttpMcpTransport.Builder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        /**
         * Forces the transport to use HTTP/1.1 instead of the default HTTP/2.
         * Cannot be combined with {@link #httpClientBuilder(HttpClientBuilder)} or {@link #httpClient(HttpClient)};
         * configure it on the HTTP client instead.
         */
        public StreamableHttpMcpTransport.Builder setHttpVersion1_1() {
            this.forceHttpVersion1_1 = true;
            return this;
        }

        /**
         * Enables or disables following HTTP redirects (3xx status codes).
         * When enabled, the transport will automatically follow redirects
         * using {@link java.net.http.HttpClient.Redirect#NORMAL} policy (always redirect,
         * except from HTTPS to HTTP).
         * Defaults to {@code false}.
         * Enabling it cannot be combined with {@link #httpClientBuilder(HttpClientBuilder)} or
         * {@link #httpClient(HttpClient)}; configure redirects on the HTTP client instead.
         */
        public StreamableHttpMcpTransport.Builder followRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
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
         * Only used with the legacy MCP protocol (versions up to 2025-11-25).
         * In modern protocol, notifications are received via {@code subscriptions/listen}.
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
