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
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The legacy HTTP/SSE transport (see <a href="https://modelcontextprotocol.io/specification/2024-11-05/basic/transports#http-with-sse">specification</a>).
 * Whenever possible, it is recommended to use {@link StreamableHttpMcpTransport} instead.
 */
@Deprecated(forRemoval = true, since = "1.4.0-beta10")
public class HttpMcpTransport implements McpTransport {

    private static final Logger log = LoggerFactory.getLogger(HttpMcpTransport.class);
    private final String sseUrl;
    private final Supplier<Map<String, String>> customHeadersSupplier;
    private final OkHttpClient client;
    private final boolean logResponses;
    private final boolean logRequests;
    private EventSource mcpSseEventListener;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private volatile Runnable onFailure;

    // this is obtained from the server after initializing the SSE channel
    private volatile String postUrl;
    private volatile McpOperationHandler messageHandler;

    public HttpMcpTransport(Builder builder) {
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
        Duration timeout = getOrDefault(builder.timeout, Duration.ofSeconds(60));
        httpClientBuilder.callTimeout(timeout);
        httpClientBuilder.connectTimeout(timeout);
        httpClientBuilder.readTimeout(timeout);
        httpClientBuilder.writeTimeout(timeout);
        this.logRequests = builder.logRequests;
        if (builder.logRequests) {
            httpClientBuilder.addInterceptor(new McpRequestLoggingInterceptor(builder.logger));
        }
        this.logResponses = builder.logResponses;
        sseUrl = ensureNotNull(builder.sseUrl, "Missing SSE endpoint URL");
        customHeadersSupplier = getOrDefault(builder.customHeadersSupplier, () -> Map::of);
        client = httpClientBuilder.build();
    }

    @Override
    public void start(McpOperationHandler messageHandler) {
        this.messageHandler = messageHandler;
        mcpSseEventListener = startSseChannel(logResponses);
    }

    @Override
    public CompletableFuture<JsonNode> initialize(McpInitializeRequest operation) {
        Request httpRequest = null;
        Request initializationNotification = null;
        try {
            httpRequest = createRequest(operation);
            initializationNotification = createRequest(new McpInitializationNotification());
        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(e);
        }
        final Request finalInitializationNotification = initializationNotification;
        return execute(httpRequest, operation.getId())
                .thenCompose(originalResponse -> execute(finalInitializationNotification, null)
                        .thenCompose(nullNode -> CompletableFuture.completedFuture(originalResponse)));
    }

    @Override
    public CompletableFuture<JsonNode> executeOperationWithResponse(McpJsonRpcMessage operation) {
        try {
            Request httpRequest = createRequest(operation);
            return execute(httpRequest, operation.getId());
        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public void executeOperationWithoutResponse(McpJsonRpcMessage operation) {
        try {
            Request httpRequest = createRequest(operation);
            execute(httpRequest, null);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void checkHealth() {
        // no transport-specific checks right now
    }

    @Override
    public void onFailure(Runnable actionOnFailure) {
        this.onFailure = actionOnFailure;
    }

    private CompletableFuture<JsonNode> execute(Request request, Long id) {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        if (id != null) {
            messageHandler.startOperation(id, future);
        }
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    int statusCode = response.code();
                    if (!isExpectedStatusCode(statusCode)) {
                        future.completeExceptionally(new RuntimeException("Unexpected status code: " + statusCode));
                    }
                    // For messages with null ID, we don't wait for a response in the SSE channel
                    if (id == null) {
                        future.complete(null);
                    }
                }
            }
        });
        return future;
    }

    private boolean isExpectedStatusCode(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private EventSource startSseChannel(boolean logResponses) {
        Request request =
                new Request.Builder().url(sseUrl).headers(buildCommonHeaders()).build();
        CompletableFuture<String> initializationFinished = new CompletableFuture<>();
        SseEventListener listener =
                new SseEventListener(messageHandler, logResponses, initializationFinished, onFailure);
        EventSource eventSource = EventSources.createFactory(client).newEventSource(request, listener);
        // wait for the SSE channel to be created, receive the POST url from the server, throw an exception if that
        // failed
        try {
            int timeout = client.callTimeoutMillis() > 0 ? client.callTimeoutMillis() : Integer.MAX_VALUE;
            String relativePostUrl = initializationFinished.get(timeout, TimeUnit.MILLISECONDS);
            postUrl = buildAbsolutePostUrl(relativePostUrl);
            log.debug("Received the server's POST URL: {}", postUrl);
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

    private Headers buildCommonHeaders() {
        Headers.Builder headerBuilder = new Headers.Builder();
        Map<String, String> headers = customHeadersSupplier.get();
        if (headers != null) {
            headers.forEach(headerBuilder::add);
        }
        return headerBuilder.build();
    }

    private Request createRequest(McpJsonRpcMessage message) throws JsonProcessingException {
        Headers.Builder headerBuilder = new Headers.Builder().add(CONTENT_TYPE, CONTENT_TYPE_JSON);
        Map<String, String> headers = customHeadersSupplier.get();
        if (headers != null) {
            headers.forEach(headerBuilder::add);
        }

        return new Request.Builder()
                .url(postUrl)
                .headers(headerBuilder.build())
                .post(RequestBody.create(OBJECT_MAPPER.writeValueAsBytes(message)))
                .build();
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String sseUrl;
        private Supplier<Map<String, String>> customHeadersSupplier;
        private Duration timeout;
        private boolean logRequests = false;
        private boolean logResponses = false;
        private Logger logger;

        /**
         * The initial URL where to connect to the server and request a SSE
         * channel.
         */
        public Builder sseUrl(String sseUrl) {
            this.sseUrl = sseUrl;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        public Builder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
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

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for traffic logging.
         * @return {@code this}.
         */
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public HttpMcpTransport build() {
            return new HttpMcpTransport(this);
        }
    }
}
