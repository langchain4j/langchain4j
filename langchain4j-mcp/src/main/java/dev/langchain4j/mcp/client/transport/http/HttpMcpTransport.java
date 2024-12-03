package dev.langchain4j.mcp.client.transport.http;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.client.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.client.protocol.McpListToolsRequest;
import dev.langchain4j.mcp.client.transport.McpTransport;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSources;

public class HttpMcpTransport implements McpTransport {

    private final String sseUrl;
    private final String postUrl;
    private final OkHttpClient client;
    private final boolean logResponses;
    private final boolean logRequests;
    private EventSource mcpSseEventListener;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Map<Long, CompletableFuture<JsonNode>> pendingOperations = new ConcurrentHashMap<>();

    public HttpMcpTransport(Builder builder) {
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
        if (builder.timeout != null) {
            httpClientBuilder.callTimeout(builder.timeout);
            httpClientBuilder.connectTimeout(builder.timeout);
            httpClientBuilder.readTimeout(builder.timeout);
            httpClientBuilder.writeTimeout(builder.timeout);
        }
        this.logRequests = builder.logRequests;
        if (builder.logRequests) {
            httpClientBuilder.addInterceptor(new McpRequestLoggingInterceptor());
        }
        this.logResponses = builder.logResponses;
        sseUrl = ensureNotNull(builder.sseUrl, "Missing SSE endpoint URL");
        postUrl = ensureNotNull(builder.postUrl, "Missing POST endpoint URL");
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
    public JsonNode executeTool(McpCallToolRequest operation) {
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
        }
    }

    private EventSource startSseChannel(boolean logResponses) {
        Request request = new Request.Builder().url(sseUrl).build();
        CompletableFuture<Void> initializationFinished = new CompletableFuture<>();
        SseEventListener listener = new SseEventListener(pendingOperations, logResponses, initializationFinished);
        EventSource eventSource = EventSources.createFactory(client).newEventSource(request, listener);
        // wait for the SSE channel to be created, throw an exception if that failed
        try {
            initializationFinished.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return eventSource;
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
        private String postUrl;
        private Duration timeout;
        private boolean logRequests = false;
        private boolean logResponses = false;

        public Builder sseUrl(String sseUrl) {
            this.sseUrl = sseUrl;
            return this;
        }

        public Builder postUrl(String postUrl) {
            this.postUrl = postUrl;
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
