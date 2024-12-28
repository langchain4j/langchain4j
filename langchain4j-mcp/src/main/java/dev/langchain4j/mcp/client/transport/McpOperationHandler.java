package dev.langchain4j.mcp.client.transport;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles incoming messages from the MCP server. Transport implementations
 * should call the "handle" method on each received message. A transport also has
 * to call "startOperation" when before starting an operation that requires a response
 * to register its ID in the map of pending operations.
 */
public class McpOperationHandler {

    private final Map<Long, CompletableFuture<JsonNode>> pendingOperations;
    private static final Logger log = LoggerFactory.getLogger(McpOperationHandler.class);

    public McpOperationHandler(Map<Long, CompletableFuture<JsonNode>> pendingOperations) {
        this.pendingOperations = pendingOperations;
    }

    public void handle(JsonNode message) {
        if (message.has("id")) {
            long messageId = message.get("id").asLong();
            CompletableFuture<JsonNode> op = pendingOperations.remove(messageId);
            if (op != null) {
                op.complete(message);
            } else {
                log.warn("Received response for unknown message id: {}", messageId);
            }
        } else {
            log.warn("Received message without id: {}", message);
        }
    }

    public void startOperation(Long id, CompletableFuture<JsonNode> future) {
        pendingOperations.put(id, future);
    }
}
