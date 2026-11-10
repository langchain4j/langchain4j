package dev.langchain4j.mcp.client.transport.websocket;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpHeadersSupplier;
import dev.langchain4j.mcp.client.logging.McpLoggers;
import dev.langchain4j.mcp.client.transport.McpJson;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpInitializationNotification;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketMcpTransport implements McpTransport {
    private static final Logger LOG = LoggerFactory.getLogger(WebSocketMcpTransport.class);
    static final int DEFAULT_MAX_MESSAGE_SIZE = 20 * 1024 * 1024;
    private final String url;
    private final McpHeadersSupplier headersSupplier;
    private final boolean logResponses;
    private final boolean logRequests;
    private final Logger trafficLog;
    private volatile McpOperationHandler operationHandler;
    private volatile McpInitializeRequest initializeRequest;
    private final Duration connectTimeout;
    private final int maxMessageSize;
    private volatile SSLContext sslContext;
    private volatile HttpClient httpClient;
    private final Executor executor;
    private final AtomicReference<CompletableFuture<WebSocket>> webSocketRef = new AtomicReference<>();
    private volatile boolean closed = false;
    private volatile Runnable actionOnFailure;

    public WebSocketMcpTransport(Builder builder) {
        this.url = ensureNotNull(builder.url, "Missing server endpoint URL");
        this.logResponses = builder.logResponses;
        this.logRequests = builder.logRequests;
        this.trafficLog = getOrDefault(builder.logger, McpLoggers.traffic());
        this.connectTimeout = getOrDefault(builder.timeout, Duration.ofSeconds(60));
        this.maxMessageSize =
                ensureGreaterThanZero(getOrDefault(builder.maxMessageSize, DEFAULT_MAX_MESSAGE_SIZE), "maxMessageSize");
        this.headersSupplier = getOrDefault(builder.headersSupplier, (i) -> Map.of());
        this.executor = builder.executor;
        this.sslContext = builder.sslContext;
        this.httpClient = createHttpClient();
    }

    private HttpClient createHttpClient() {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder().connectTimeout(connectTimeout);
        if (sslContext != null) {
            clientBuilder.sslContext(sslContext);
        }
        if (executor != null) {
            clientBuilder.executor(executor);
        }
        return clientBuilder.build();
    }

    private synchronized WebSocket getWebSocket() {
        try {
            CompletableFuture<WebSocket> future = this.webSocketRef.get();
            if (future == null) {
                return startWebSocket().get();
            }
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            // if websocket initialization failed, try again once
            try {
                return startWebSocket().get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            } catch (ExecutionException ex) {
                // if it failed again, bail out
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public void start(McpOperationHandler operationHandler) {
        this.operationHandler = operationHandler;
        startWebSocket();
    }

    private synchronized CompletableFuture<WebSocket> startWebSocket() {
        // if one is already initializing, just return its future
        CompletableFuture<WebSocket> current = this.webSocketRef.get();
        if (current != null && !current.isDone()) {
            return current;
        }

        // if there is no initialization in progress (this means either the current one is broken
        // or we are initializing for the first time) -> start a new one
        WebSocket.Builder builder = this.httpClient.newWebSocketBuilder();
        headersSupplier.apply(null).forEach((key, value) -> builder.header(key, value));
        builder.connectTimeout(connectTimeout);
        CompletableFuture<WebSocket> newWebSocketFuture = builder.buildAsync(
                URI.create(url),
                new WebSocketMcpListener(
                        operationHandler,
                        trafficLog,
                        logResponses,
                        () -> {
                            // On close callback: clear the webSocketRef so that a new connection can be created when
                            // needed.
                            // Don't eagerly re-create it because the WebSocket transport does not have proper recovery
                            // capabilities,
                            // so all running operations were lost anyway.
                            webSocketRef.set(null);
                        },
                        actionOnFailure,
                        maxMessageSize));
        // if the initialize method was already called and we know the necessary data for initialization, schedule
        // a new initialization right away
        if (this.initializeRequest != null) {
            newWebSocketFuture = newWebSocketFuture.thenCompose(webSocket -> execute(
                            new McpCallContext(null, this.initializeRequest),
                            Optional.of(webSocket),
                            this.initializeRequest.getId())
                    .thenCompose(originalResponse -> execute(
                                    new McpCallContext(null, new McpInitializationNotification()),
                                    Optional.of(webSocket),
                                    null)
                            .thenCompose(nullNode -> CompletableFuture.completedFuture(webSocket))));
        }
        this.webSocketRef.set(newWebSocketFuture);
        return newWebSocketFuture;
    }

    @Override
    public CompletableFuture<String> sendInitializeRequest(McpInitializeRequest operation) {
        this.initializeRequest = operation;
        CompletableFuture<String> completableFuture =
                execute(new McpCallContext(null, operation), Optional.empty(), operation.getId());
        return completableFuture
                .thenCompose(originalResponse -> {
                    return CompletableFuture.completedFuture(originalResponse);
                })
                .thenCompose(originalResponse -> execute(
                                new McpCallContext(null, new McpInitializationNotification()), Optional.empty(), null)
                        .thenCompose(nullNode -> CompletableFuture.completedFuture(originalResponse)));
    }

    @Override
    public CompletableFuture<String> sendRequest(McpCallContext context) {
        return execute(context, Optional.empty(), context.message().getId());
    }

    @Override
    public void sendMessage(McpCallContext context) {
        execute(context, Optional.empty(), null);
    }

    @Override
    public void checkHealth() {
        // no transport-specific checks right now
    }

    @Override
    public boolean requiresCancellationNotification() {
        return true;
    }

    @Override
    public void onFailure(Runnable actionOnFailure) {
        this.actionOnFailure = actionOnFailure;
    }

    /**
     * Closes the connection, if any, and forgets the initialize request a reconnection would replay, without closing
     * the HTTP client: the next {@link #start} opens a new connection.
     */
    @Override
    public void stop() {
        initializeRequest = null;
        closeWebSocket(webSocketRef.getAndSet(null));
    }

    @Override
    public void close() throws IOException {
        this.closed = true;
        closeWebSocket(webSocketRef.get());
        // The httpClient.close() method only exists on JDK 21+, so invoke it only if we can.
        // Replace this with a normal method call when switching the base to JDK 21+.
        try {
            httpClient.getClass().getMethod("close").invoke(httpClient);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        }
    }

    private void closeWebSocket(CompletableFuture<WebSocket> future) {
        if (future == null) {
            return;
        }
        if (!future.isDone()) {
            // still connecting: close the connection once it is open, instead of leaving it behind
            future.thenAccept(webSocket -> webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client closing"));
            return;
        }
        try {
            WebSocket webSocket = future.get();
            webSocket
                    .sendClose(WebSocket.NORMAL_CLOSURE, "Client closing")
                    .thenRun(() -> LOG.info("WebSocket connection closed"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            if (e.getCause() != null && e.getCause() instanceof ConnectException) {
                // this means the connection was previously attempted but failed, so nothing to close
            } else {
                LOG.warn("Failed to close WebSocket connection", e);
            }
        }
    }

    private CompletableFuture<String> execute(McpCallContext context, Optional<WebSocket> webSocket, Long id) {
        CompletableFuture<String> future = new CompletableFuture<>();
        if (closed) {
            future.completeExceptionally(new IllegalStateException("Transport is closed"));
            return future;
        }
        if (id != null) {
            operationHandler.expectResponse(id, future);
        }
        try {
            String messageJson = McpJson.serialize(context.message());
            WebSocket wsToUse = webSocket.orElseGet(() -> getWebSocket());
            if (logRequests) {
                trafficLog.info("> " + messageJson);
            }
            synchronized (wsToUse) {
                wsToUse.sendText(messageJson, true).whenComplete((ws, error) -> {
                    if (error != null) {
                        future.completeExceptionally(error);
                    } else if (id == null) {
                        // for operations without an ID, consider them done immediately after the message was sent
                        future.complete(null);
                    }
                });
            }
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * Reloads the SSL context used by the transport.
     * From this point on, new websocket connections will use the updated SSL context.
     * This does NOT cancel or restart the existing connection, so this takes effect only
     * after the existing connection stops working.
     */
    public void reloadSslContext(SSLContext sslContext) {
        ensureNotNull(sslContext, "sslContext");

        this.sslContext = sslContext;
        this.httpClient = createHttpClient();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean logResponses;
        private boolean logRequests;
        private String url;
        private Logger logger;
        private Executor executor;
        private Duration timeout;
        private SSLContext sslContext;
        private McpHeadersSupplier headersSupplier;
        private Integer maxMessageSize;

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        /**
         * Sets a custom {@link Logger} to be used for websocket traffic logging.
         * If not specified, a default logger will be used.
         *
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for traffic logging.
         * @return {@code this}.
         */
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * An optional {@link Executor} that will be used for executing requests and handling responses.
         */
        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * The connection timeout (applied on the websocket client level). Application-level
         * timeouts are handled by the MCP client itself.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public Builder headersSupplier(Supplier<Map<String, String>> headersSupplier) {
            this.headersSupplier = (i) -> headersSupplier.get();
            return this;
        }

        public Builder headersSupplier(McpHeadersSupplier headersSupplier) {
            this.headersSupplier = headersSupplier;
            return this;
        }

        /**
         * The maximum size, in bytes, of a single message received from the server.
         * When a server sends a larger message, the transport closes the connection with status 1009
         * (message too big) and all pending requests fail. This protects the client against servers that send
         * an endless or huge message, which would otherwise be buffered in memory until it runs out.
         * The size is that of the JSON text, so binary content such as images, which is base64-encoded,
         * takes about a third more than its raw size.
         * Defaults to 20 MiB.
         *
         * @param maxMessageSize the maximum message size in bytes, must be greater than zero;
         *                       {@code null} means the default
         * @return this builder
         * @since 1.24.0
         */
        public Builder maxMessageSize(Integer maxMessageSize) {
            this.maxMessageSize = maxMessageSize;
            return this;
        }

        public WebSocketMcpTransport build() {
            return new WebSocketMcpTransport(this);
        }
    }

}
