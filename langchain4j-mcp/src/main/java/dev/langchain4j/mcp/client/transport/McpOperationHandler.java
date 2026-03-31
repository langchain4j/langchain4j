package dev.langchain4j.mcp.client.transport;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.McpRoot;
import dev.langchain4j.mcp.client.logging.McpLogMessage;
import dev.langchain4j.mcp.client.progress.McpProgressHandler;
import dev.langchain4j.mcp.client.progress.McpProgressNotification;
import dev.langchain4j.mcp.protocol.McpPingResponse;
import dev.langchain4j.mcp.protocol.McpRootsListResponse;
import dev.langchain4j.mcp.protocol.McpServerMethod;
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
 * to call "startOperation" before starting an operation that requires a response
 * to register its ID in the map of pending operations.
 */
public class McpOperationHandler {

    private final Map<Long, CompletableFuture<JsonNode>> pendingOperations;
    private static final Logger log = LoggerFactory.getLogger(McpOperationHandler.class);
    private final McpTransport transport;
    private final Consumer<McpLogMessage> logMessageConsumer;
    private final Runnable onToolListUpdate;
    private final Runnable onResourceListUpdate;
    private final Runnable onPromptListUpdate;
    private final Supplier<List<McpRoot>> roots;
    private final McpProgressHandler progressHandler;

    public McpOperationHandler(
            Map<Long, CompletableFuture<JsonNode>> pendingOperations,
            Supplier<List<McpRoot>> roots,
            McpTransport transport,
            Consumer<McpLogMessage> logMessageConsumer,
            Runnable onToolListUpdate,
            Runnable onResourceListUpdate,
            Runnable onPromptListUpdate,
            McpProgressHandler progressHandler) {
        this.pendingOperations = pendingOperations;
        this.transport = transport;
        this.logMessageConsumer = logMessageConsumer;
        this.onToolListUpdate = onToolListUpdate;
        this.onResourceListUpdate = onResourceListUpdate;
        this.onPromptListUpdate = onPromptListUpdate;
        this.roots = roots;
        this.progressHandler = progressHandler;
    }

    public void handle(JsonNode message) {
        if (message.has("id")) {
            handleMessageWithId(message);
        } else if (message.has("method")) {
            handleNotification(message);
        }
    }

    private void handleMessageWithId(JsonNode message) {
        long messageId = message.get("id").asLong();
        if (message.has("result") || message.has("error")) {
            // response to a client-initiated operation
            CompletableFuture<JsonNode> op = pendingOperations.remove(messageId);
            if (op != null) {
                op.complete(message);
            } else {
                log.warn("Received response for unknown message id: {}", messageId);
            }
        } else if (message.has("method")) {
            // server-initiated request requiring a response
            McpServerMethod method = McpServerMethod.from(message.get("method").asText());
            if (method == null) {
                log.warn("Received response for unknown message id: {}", messageId);
                return;
            }
            switch (method) {
                case PING:
                    transport.executeOperationWithoutResponse(new McpPingResponse(messageId));
                    break;
                case ROOTS_LIST:
                    transport.executeOperationWithoutResponse(new McpRootsListResponse(messageId, roots.get()));
                    break;
                default:
                    log.warn("Received response for unknown message id: {}", messageId);
            }
        } else {
            log.warn("Received response for unknown message id: {}", messageId);
        }
    }

    private void handleNotification(JsonNode message) {
        McpServerMethod method = McpServerMethod.from(message.get("method").asText());
        if (method == null) {
            log.warn("Received unknown message: {}", message);
            return;
        }
        switch (method) {
            case NOTIFICATION_MESSAGE:
                handleLogMessage(message);
                break;
            case NOTIFICATION_TOOLS_LIST_CHANGED:
                onToolListUpdate.run();
                break;
            case NOTIFICATION_RESOURCES_LIST_CHANGED:
                if (onResourceListUpdate != null) {
                    onResourceListUpdate.run();
                }
                break;
            case NOTIFICATION_PROMPTS_LIST_CHANGED:
                if (onPromptListUpdate != null) {
                    onPromptListUpdate.run();
                }
                break;
            case NOTIFICATION_PROGRESS:
                handleProgressNotification(message);
                break;
            default:
                log.warn("Received unknown message: {}", message);
        }
    }

    private void handleLogMessage(JsonNode message) {
        if (message.has("params")) {
            if (logMessageConsumer != null) {
                logMessageConsumer.accept(McpLogMessage.fromJson(message.get("params")));
            }
        } else {
            log.warn("Received log message without params: {}", message);
        }
    }

    private void handleProgressNotification(JsonNode message) {
        if (progressHandler != null && message.has("params")) {
            progressHandler.onProgress(McpProgressNotification.fromJson(message.get("params")));
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
