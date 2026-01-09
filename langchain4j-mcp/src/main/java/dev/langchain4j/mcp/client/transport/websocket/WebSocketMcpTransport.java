package dev.langchain4j.mcp.client.transport.websocket;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpInitializationNotification;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
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

    private static final Logger DEFAULT_TRAFFIC_LOG = LoggerFactory.getLogger("MCP");
    private static final Logger LOG = LoggerFactory.getLogger(WebSocketMcpTransport.class);
    private final String url;
    private final Supplier<Map<String, String>> headersSupplier;
    private final boolean logResponses;
    private final boolean logRequests;
    private final Logger trafficLog;
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private volatile McpOperationHandler operationHandler;
    private volatile McpInitializeRequest initializeRequest;
    private final Duration connectTimeout;
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
        this.trafficLog = getOrDefault(builder.logger, DEFAULT_TRAFFIC_LOG);
        this.connectTimeout = getOrDefault(builder.timeout, Duration.ofSeconds(60));
        this.headersSupplier = getOrDefault(builder.headersSupplier, (Supplier<Map<String, String>>) () -> Map.of());
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
        headersSupplier.get().forEach((key, value) -> builder.header(key, value));
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
                        actionOnFailure));
        // if the initialize method was already called and we know the necessary data for initialization, schedule
        // a new initialization right away
        if (this.initializeRequest != null) {
            newWebSocketFuture = newWebSocketFuture.thenCompose(
                    webSocket -> execute(this.initializeRequest, this.initializeRequest.getId(), Optional.of(webSocket))
                            .thenCompose(originalResponse -> execute(
                                            new McpInitializationNotification(), null, Optional.of(webSocket))
                                    .thenCompose(nullNode -> CompletableFuture.completedFuture(webSocket))));
        }
        this.webSocketRef.set(newWebSocketFuture);
        return newWebSocketFuture;
    }

    @Override
    public CompletableFuture<JsonNode> initialize(McpInitializeRequest operation) {
        this.initializeRequest = operation;
        CompletableFuture<JsonNode> completableFuture = execute(operation, operation.getId(), Optional.empty());
        return completableFuture
                .thenCompose(originalResponse -> {
                    return CompletableFuture.completedFuture(originalResponse);
                })
                .thenCompose(originalResponse -> execute(new McpInitializationNotification(), null, Optional.empty())
                        .thenCompose(nullNode -> CompletableFuture.completedFuture(originalResponse)));
    }

    @Override
    public CompletableFuture<JsonNode> executeOperationWithResponse(McpJsonRpcMessage request) {
        return execute(request, request.getId(), Optional.empty());
    }

    @Override
    public void executeOperationWithoutResponse(McpJsonRpcMessage request) {
        execute(request, null, Optional.empty());
    }

    @Override
    public void checkHealth() {
        // no transport-specific checks right now
    }

    @Override
    public void onFailure(Runnable actionOnFailure) {
        this.actionOnFailure = actionOnFailure;
    }

    @Override
    public void close() throws IOException {
        this.closed = true;
        CompletableFuture<WebSocket> future = webSocketRef.get();
        if (future != null) {
            if (future.isDone()) {
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
        }
        // The httpClient.close() method only exists on JDK 21+, so invoke it only if we can.
        // Replace this with a normal method call when switching the base to JDK 21+.
        try {
            httpClient.getClass().getMethod("close").invoke(httpClient);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        }
    }

    private CompletableFuture<JsonNode> execute(McpJsonRpcMessage message, Long id, Optional<WebSocket> webSocket) {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        if (closed) {
            future.completeExceptionally(new IllegalStateException("Transport is closed"));
            return future;
        }
        if (id != null) {
            operationHandler.startOperation(id, future);
        }
        try {
            String messageJson = OBJECT_MAPPER.writeValueAsString(message);
            WebSocket wsToUse = webSocket.orElseGet(() -> getWebSocket());
            if (logRequests) {
                trafficLog.info("> " + messageJson);
            }
            synchronized (wsToUse) {
                wsToUse.sendText(messageJson, true).thenAccept(ws -> {
                    if (id == null) {
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
        private Supplier<Map<String, String>> headersSupplier;

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
            this.headersSupplier = headersSupplier;
            return this;
        }

        public WebSocketMcpTransport build() {
            return new WebSocketMcpTransport(this);
        }
    }
}
