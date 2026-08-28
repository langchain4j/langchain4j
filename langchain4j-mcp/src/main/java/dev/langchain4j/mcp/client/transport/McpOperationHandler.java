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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles incoming messages from the MCP server.
 *
 * <p>A transport calls {@link #onMessage(String)} for every message it receives, and
 * {@link #expectResponse(Long, java.util.concurrent.CompletableFuture)} before sending a request,
 * to register the id whose response it is waiting for.
 *
 * <p>The {@code handle} and {@code startOperation} methods do the same through Jackson's
 * {@code JsonNode} and are deprecated.
 */
public class McpOperationHandler {

    private final Map<Long, CompletableFuture<String>> pendingOperations;
    private static final Logger log = LoggerFactory.getLogger(McpOperationHandler.class);
    private final McpTransport transport;
    private final Consumer<McpLogMessage> logMessageConsumer;
    private final Runnable onToolListUpdate;
    private final Runnable onResourceListUpdate;
    private final Runnable onPromptListUpdate;
    private final Consumer<String> onResourceUpdate;
    private final Supplier<List<McpRoot>> roots;
    private final McpProgressHandler progressHandler;
    private final Runnable onServerPing;
    private final Runnable onServerRootsList;
    private final BiConsumer<Long, String> onServerCancelled;
    private final BiConsumer<Long, Map<String, Object>> onSubscriptionAcknowledged;

    public McpOperationHandler(
            Map<Long, CompletableFuture<String>> pendingOperations,
            Supplier<List<McpRoot>> roots,
            McpTransport transport,
            Consumer<McpLogMessage> logMessageConsumer,
            Runnable onToolListUpdate,
            Runnable onResourceListUpdate,
            Runnable onPromptListUpdate,
            Consumer<String> onResourceUpdate,
            McpProgressHandler progressHandler,
            Runnable onServerPing,
            Runnable onServerRootsList,
            BiConsumer<Long, String> onServerCancelled,
            BiConsumer<Long, Map<String, Object>> onSubscriptionAcknowledged) {
        this.pendingOperations = pendingOperations;
        this.transport = transport;
        this.logMessageConsumer = logMessageConsumer;
        this.onToolListUpdate = onToolListUpdate;
        this.onResourceListUpdate = onResourceListUpdate;
        this.onPromptListUpdate = onPromptListUpdate;
        this.onResourceUpdate = onResourceUpdate;
        this.roots = roots;
        this.progressHandler = progressHandler;
        this.onServerPing = onServerPing;
        this.onServerRootsList = onServerRootsList;
        this.onServerCancelled = onServerCancelled;
        this.onSubscriptionAcknowledged = onSubscriptionAcknowledged;
    }

    /**
     * Handles an inbound message from the server.
     *
     * @deprecated use {@link #onMessage(String)} instead, which does not expose Jackson types.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    public void handle(JsonNode message) {
        onMessage(McpJson.serialize(message));
    }

    /**
     * Handles an inbound message from the server, taken exactly as it arrived on the wire, so that
     * a transport does not have to parse it first.
     *
     * <p>Valid JSON that is not a JSON-RPC object - a batch array, a scalar, a bare null - is
     * ignored. Text that is not valid JSON at all throws, so that the transport can decide: fail
     * the pending operation, or log and keep reading.
     *
     * @throws IllegalArgumentException if the message is not valid JSON, or
     * {@link dev.langchain4j.exception.JsonException} from a codec that reports the typed exceptions.
     */
    @SuppressWarnings("unchecked")
    public void onMessage(String json) {
        // Malformed input is the caller's decision, not this method's: a transport whose response
        // body is unreadable has to fail the pending operation, while a stream reader only logs it
        // and carries on. Both behaved that way before the parse moved in here.
        Object parsed = McpJson.toValue(json);
        if (!(parsed instanceof Map)) {
            // valid JSON, but not a JSON-RPC object - a batch array, a bare null or a scalar.
            // The tree model ignored these rather than failing, so they stay ignored.
            log.warn("Received a message that is not a JSON-RPC object: {}", json);
            return;
        }
        Map<String, Object> message = (Map<String, Object>) parsed;
        if (message.containsKey("id")) {
            handleMessageWithId(message, json);
        } else if (message.containsKey("method")) {
            handleNotification(message);
        }
    }

    private void handleMessageWithId(Map<String, Object> message, String json) {
        Long messageId = toLong(message.get("id"));
        if (messageId == null) {
            log.warn("Received message with an unusable id: {}", message);
            return;
        }
        if (message.containsKey("result") || message.containsKey("error")) {
            // response to a client-initiated operation
            CompletableFuture<String> op = pendingOperations.remove(messageId);
            if (op != null) {
                op.complete(json);
            } else {
                log.warn("Received response for unknown message id: {}", messageId);
            }
        } else if (message.containsKey("method")) {
            // server-initiated request requiring a response
            McpServerMethod method = McpServerMethod.from(String.valueOf(message.get("method")));
            if (method == null) {
                log.warn("Received response for unknown message id: {}", messageId);
                return;
            }
            switch (method) {
                case PING:
                    transport.sendMessage(new McpPingResponse(messageId));
                    if (onServerPing != null) {
                        onServerPing.run();
                    }
                    break;
                case ROOTS_LIST:
                    transport.sendMessage(new McpRootsListResponse(messageId, roots.get()));
                    if (onServerRootsList != null) {
                        onServerRootsList.run();
                    }
                    break;
                default:
                    log.warn("Received response for unknown message id: {}", messageId);
            }
        } else {
            log.warn("Received response for unknown message id: {}", messageId);
        }
    }

    private void handleNotification(Map<String, Object> message) {
        McpServerMethod method = McpServerMethod.from(String.valueOf(message.get("method")));
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
            case NOTIFICATION_RESOURCES_UPDATED:
                handleResourceUpdatedNotification(message);
                break;
            case NOTIFICATION_PROGRESS:
                handleProgressNotification(message);
                break;
            case NOTIFICATION_CANCELLED:
                handleCancelledNotification(message);
                break;
            case NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED:
                handleSubscriptionAcknowledgedNotification(message);
                break;
            default:
                log.warn("Received unknown message: {}", message);
        }
    }

    private void handleCancelledNotification(Map<String, Object> message) {
        Map<String, Object> params = params(message);
        Long requestId = params == null ? null : toLong(params.get("requestId"));
        if (requestId == null) {
            log.warn("Received cancelled notification without requestId: {}", message);
            return;
        }
        Object reasonValue = params.get("reason");
        String reason = reasonValue == null ? null : String.valueOf(reasonValue);
        CompletableFuture<String> pending = pendingOperations.remove(requestId);
        if (pending != null) {
            String message1 = reason != null
                    ? "Request " + requestId + " was cancelled by the server: " + reason
                    : "Request " + requestId + " was cancelled by the server";
            pending.completeExceptionally(new CancellationException(message1));
        } else {
            log.debug(
                    "Received cancelled notification for unknown or already completed request id: {} (reason: {})",
                    requestId,
                    reason);
        }
        if (onServerCancelled != null) {
            onServerCancelled.accept(requestId, reason);
        }
    }

    private void handleLogMessage(Map<String, Object> message) {
        Map<String, Object> params = params(message);
        if (params != null) {
            if (logMessageConsumer != null) {
                logMessageConsumer.accept(McpLogMessage.fromMap(params));
            }
        } else {
            log.warn("Received log message without params: {}", message);
        }
    }

    private void handleResourceUpdatedNotification(Map<String, Object> message) {
        Map<String, Object> params = params(message);
        if (params == null) {
            return;
        }
        Object uri = params.get("uri");
        if (uri == null) {
            log.warn("Received resource updated notification without uri: {}", message);
        } else if (onResourceUpdate != null) {
            onResourceUpdate.accept(String.valueOf(uri));
        }
    }

    private void handleSubscriptionAcknowledgedNotification(Map<String, Object> message) {
        Map<String, Object> params = params(message);
        Object meta = params == null ? null : params.get("_meta");
        Long subscriptionId = meta instanceof Map
                ? toLong(((Map<?, ?>) meta).get("io.modelcontextprotocol/subscriptionId"))
                : null;
        if (subscriptionId == null) {
            log.warn("Received subscriptions acknowledged notification without a subscription ID: {}", message);
            return;
        }
        if (onSubscriptionAcknowledged != null) {
            onSubscriptionAcknowledged.accept(subscriptionId, message);
        }
    }

    private void handleProgressNotification(Map<String, Object> message) {
        Map<String, Object> params = params(message);
        if (progressHandler != null && params != null) {
            progressHandler.onProgress(McpProgressNotification.fromMap(params));
        }
    }

    /**
     * JSON-RPC allows an id to be a number or a string, and Jackson coerced both, so both are
     * accepted here too.
     */
    private static Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> params(Map<String, Object> message) {
        Object params = message.get("params");
        return params instanceof Map ? (Map<String, Object>) params : null;
    }

    /**
     * Registers a client-initiated request whose response is awaited.
     *
     * @deprecated use {@link #expectResponse(Long, CompletableFuture)} instead, which does not
     * expose Jackson types.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    public void startOperation(Long id, CompletableFuture<JsonNode> future) {
        CompletableFuture<String> jsonFuture = new CompletableFuture<>();
        jsonFuture.whenComplete((json, error) -> {
            if (error != null) {
                future.completeExceptionally(error);
                return;
            }
            try {
                future.complete(McpJson.parse(json));
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
        expectResponse(id, jsonFuture);
    }

    /**
     * Registers a client-initiated request whose response is awaited; the future is completed with
     * the response as unparsed JSON text.
     */
    public void expectResponse(Long id, CompletableFuture<String> future) {
        pendingOperations.put(id, future);
    }

    public synchronized void cancelAllPendingOperations(String reason) {
        for (CompletableFuture<String> future : pendingOperations.values()) {
            future.completeExceptionally(
                    new IllegalStateException("Operation cancelled due to transport failure: " + reason));
        }
        pendingOperations.clear();
    }
}
