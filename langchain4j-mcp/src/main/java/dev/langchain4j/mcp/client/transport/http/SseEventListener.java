package dev.langchain4j.mcp.client.transport.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SseEventListener extends EventSourceListener {

    private final Map<Long, CompletableFuture<JsonNode>> pendingOperations;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(SseEventListener.class);
    private final boolean logEvents;
    // this will contain the POST url for sending commands to the server
    private final CompletableFuture<String> initializationFinished;

    public SseEventListener(
            Map<Long, CompletableFuture<JsonNode>> pendingOperations,
            boolean logEvents,
            CompletableFuture initializationFinished) {
        this.pendingOperations = pendingOperations;
        this.logEvents = logEvents;
        this.initializationFinished = initializationFinished;
    }

    @Override
    public void onClosed(EventSource eventSource) {
        log.debug("SSE channel closed");
    }

    @Override
    public void onEvent(EventSource eventSource, String id, String type, String data) {
        if (type.equals("endpoint")) {
            if (initializationFinished.isDone()) {
                log.warn("Received endpoint event after initialization");
                return;
            }
            initializationFinished.complete(data);
        } else if (type.equals("message")) {
            if (logEvents) {
                log.debug("< {}", data);
            }
            try {
                JsonNode message = OBJECT_MAPPER.readValue(data, JsonNode.class);
                long messageId = message.get("id").asLong();
                CompletableFuture<JsonNode> op = pendingOperations.remove(messageId);
                if (op != null) {
                    op.complete(message);
                } else {
                    log.warn("Received response for unknown message id: {}", messageId);
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse response data", e);
            }
        }
    }

    @Override
    public void onFailure(EventSource eventSource, Throwable t, Response response) {
        if (!initializationFinished.isDone()) {
            initializationFinished.completeExceptionally(t);
        }
        if (t != null && (t.getMessage() == null || !t.getMessage().contains("Socket closed"))) {
            log.warn("SSE channel failure", t);
        }
    }

    @Override
    public void onOpen(EventSource eventSource, Response response) {
        log.debug("Connected to SSE channel at {}", response.request().url());
    }
}
