package dev.langchain4j.mcp.client.transport;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.McpRoot;
import dev.langchain4j.mcp.client.logging.McpLogMessage;
import dev.langchain4j.mcp.protocol.McpPingResponse;
import dev.langchain4j.mcp.protocol.McpRootsListResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
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
    private final McpTransport transport;
    private final Consumer<McpLogMessage> logMessageConsumer;
    private final Runnable onToolListUpdate;
    private final Supplier<List<McpRoot>> roots;

    public McpOperationHandler(
            Map<Long, CompletableFuture<JsonNode>> pendingOperations,
            Supplier<List<McpRoot>> roots,
            McpTransport transport,
            Consumer<McpLogMessage> logMessageConsumer,
            Runnable onToolListUpdate) {
        this.pendingOperations = pendingOperations;
        this.transport = transport;
        this.logMessageConsumer = logMessageConsumer;
        this.onToolListUpdate = onToolListUpdate;
        this.roots = roots;
    }

    public void handle(JsonNode message) {
        if (message.has("id")) {
            long messageId = message.get("id").asLong();
            if (message.has("result") || message.has("error")) {
                // if there is a result or error, we assume that this is related to a client-initiated operation
                CompletableFuture<JsonNode> op = pendingOperations.remove(messageId);
                if (op != null) {
                    op.complete(message);
                } else {
                    log.warn("Received response for unknown message id: {}", messageId);
                }
            } else {
                // this is a server-initiated operation, the pendingOperations map is not relevant
                if (message.has("method")) {
                    String method = message.get("method").asText();
                    if (method.equals("ping")) {
                        transport.executeOperationWithoutResponse(new McpPingResponse(messageId));
                        return;
                    } else if (method.equals("roots/list")) {
                        transport.executeOperationWithoutResponse(new McpRootsListResponse(messageId, roots.get()));
                        return;
                    }
                }
                log.warn("Received response for unknown message id: {}", messageId);
            }
        } else if (message.has("method")) {
            String method = message.get("method").asText();
            if (method.equals("notifications/message")) {
                // this is a log message
                if (message.has("params")) {
                    if (logMessageConsumer != null) {
                        logMessageConsumer.accept(McpLogMessage.fromJson(message.get("params")));
                    }
                } else {
                    log.warn("Received log message without params: {}", message);
                }
            } else if (method.equals("notifications/tools/list_changed")) {
                onToolListUpdate.run();
            } else {
                log.warn("Received unknown message: {}", message);
            }
        }
    }

    public void startOperation(Long id, CompletableFuture<JsonNode> future) {
        pendingOperations.put(id, future);
    }

    public synchronized void cancelAllPendingOperations(String reason) {
        for (CompletableFuture<JsonNode> future : pendingOperations.values()) {
            future.completeExceptionally(
                    new IllegalStateException("Operation cancelled due to transport failure: " + reason));
        }
        pendingOperations.clear();
    }
}
