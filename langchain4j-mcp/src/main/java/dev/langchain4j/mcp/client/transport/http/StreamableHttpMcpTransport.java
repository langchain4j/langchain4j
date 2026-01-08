package dev.langchain4j.mcp.client.transport.http;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamableHttpMcpTransport implements McpTransport {

    private static final Logger DEFAULT_TRAFFIC_LOG = LoggerFactory.getLogger("MCP");
    private static final Logger LOG = LoggerFactory.getLogger(StreamableHttpMcpTransport.class);
    private final String url;
    private final Supplier<Map<String, String>> customHeadersSupplier;
    private final boolean logResponses;
    private final boolean logRequests;
    private final Logger trafficLog;
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final AtomicReference<CompletableFuture<JsonNode>> initializeInProgress = new AtomicReference<>(null);
    private volatile McpOperationHandler operationHandler;
    private final HttpClient httpClient;
    private final SSLContext sslContext;
    private McpInitializeRequest initializeRequest;
    private final AtomicReference<String> mcpSessionId = new AtomicReference<>();

    public StreamableHttpMcpTransport(StreamableHttpMcpTransport.Builder builder) {
        url = ensureNotNull(builder.url, "Missing server endpoint URL");
        logRequests = builder.logRequests;
        logResponses = builder.logResponses;
        trafficLog = getOrDefault(builder.logger, DEFAULT_TRAFFIC_LOG);
        Duration timeout = getOrDefault(builder.timeout, Duration.ofSeconds(60));
        customHeadersSupplier = getOrDefault(builder.customHeadersSupplier, () -> Map::of);
        sslContext = builder.sslContext;
        HttpClient.Builder clientBuilder = HttpClient.newBuilder().connectTimeout(timeout);
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
        CompletableFuture<JsonNode> completableFuture = execute(operation, operation.getId());
        initializeInProgress.set(completableFuture);
        return completableFuture
                .thenCompose(originalResponse -> {
                    initializeInProgress.set(null);
                    return CompletableFuture.completedFuture(originalResponse);
                })
                .thenCompose(originalResponse -> execute(new McpInitializationNotification(), null)
                        .thenCompose(nullNode -> CompletableFuture.completedFuture(originalResponse)));
    }

    private HttpRequest createRequest(McpJsonRpcMessage message) throws JsonProcessingException {
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
        Map<String, String> headers = customHeadersSupplier.get();
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
    public CompletableFuture<JsonNode> executeOperationWithResponse(McpJsonRpcMessage operation) {
        return execute(operation, operation.getId());
    }

    @Override
    public void executeOperationWithoutResponse(McpJsonRpcMessage operation) {
        execute(operation, null);
    }

    @Override
    public void checkHealth() {
        // no transport-specific checks right now
    }

    @Override
    public void onFailure(Runnable actionOnFailure) {
        // nothing to do here, we don't maintain a long-running SSE channel (yet)
    }

    private CompletableFuture<JsonNode> execute(McpJsonRpcMessage message, Long id) {
        return execute(message, id, false);
    }

    private CompletableFuture<JsonNode> execute(McpJsonRpcMessage message, Long id, boolean isRetry) {
        if (!(message instanceof McpInitializeRequest)) {
            CompletableFuture<JsonNode> reinitializeInProgress = this.initializeInProgress.get();
            if (reinitializeInProgress != null) {
                reinitializeInProgress.join();
            }
        }
        HttpRequest request = null;
        try {
            request = createRequest(message);
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
                        if (!(message instanceof McpInitializeRequest) && responseInfo.statusCode() == 404) {
                            if (!isRetry) {
                                initialize(StreamableHttpMcpTransport.this.initializeRequest)
                                        .thenAccept(node -> {
                                            execute(message, id, true)
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

    private boolean isExpectedStatusCode(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    @Override
    public void close() throws IOException {
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
        private Supplier<Map<String, String>> customHeadersSupplier;
        private Duration timeout;
        private boolean logRequests = false;
        private boolean logResponses = false;
        private Logger logger;
        private SSLContext sslContext;

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
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * A supplier for dynamic request headers of the MCP server.
         * The supplier is called for each request, allowing headers to be updated dynamically.
         */
        public StreamableHttpMcpTransport.Builder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
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

        public StreamableHttpMcpTransport build() {
            return new StreamableHttpMcpTransport(this);
        }
    }
}
