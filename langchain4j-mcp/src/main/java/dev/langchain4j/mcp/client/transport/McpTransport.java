package dev.langchain4j.mcp.client.transport;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

/**
 * The transport contract between the MCP client and a server connection.
 *
 * <p>Each request/response method exists in two forms: one returning the response as JSON text,
 * and a deprecated one returning Jackson's {@code JsonNode}. Both are {@code default} methods that
 * delegate to each other, so an implementation must override <b>one of each pair</b>; overriding
 * neither compiles but fails at runtime. New implementations should override the JSON-text forms,
 * which are the ones the client calls.
 */
public interface McpTransport extends Closeable {

    /**
     * Creates a connection to the MCP server (runs the server as a subprocess if needed).
     * This does NOT yet send the "initialize" message to negotiate capabilities.
     */
    void start(McpOperationHandler messageHandler);

    /**
     * Sends the "initialize" message to the MCP server to negotiate
     * capabilities, supported protocol version etc. When this method
     * returns successfully, the transport is fully initialized and ready to
     * be used. This has to be called AFTER the "start" method.
     * Only used with the legacy MCP protocol (versions up to 2025-11-25).
     * Modern protocol uses {@code server/discover} instead.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    default CompletableFuture<JsonNode> initialize(McpInitializeRequest request) {
        throw new UnsupportedOperationException(
                "Implement initializeJson(McpInitializeRequest) instead of initialize(McpInitializeRequest)");
    }

    /**
     * JSON-text counterpart of {@link #initialize(McpInitializeRequest)}, returning the
     * server's response as unparsed JSON text so that transports do not need a JSON library.
     */
    default CompletableFuture<String> initializeJson(McpInitializeRequest request) {
        return McpJson.map(initialize(request), McpJson::serialize);
    }

    /**
     * Executes an operation that expects a response from the server.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    default CompletableFuture<JsonNode> executeOperationWithResponse(McpClientMessage request) {
        throw new UnsupportedOperationException("Implement executeOperationWithJsonResponse(McpClientMessage) instead"
                + " of executeOperationWithResponse(McpClientMessage)");
    }

    /**
     * JSON-text counterpart of {@link #executeOperationWithResponse(McpClientMessage)}.
     */
    default CompletableFuture<String> executeOperationWithJsonResponse(McpClientMessage request) {
        return McpJson.map(executeOperationWithResponse(request), McpJson::serialize);
    }

    /**
     * Executes an operation that expects a response from the server.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    default CompletableFuture<JsonNode> executeOperationWithResponse(McpCallContext context) {
        throw new UnsupportedOperationException("Implement executeOperationWithJsonResponse(McpCallContext) instead"
                + " of executeOperationWithResponse(McpCallContext)");
    }

    /**
     * JSON-text counterpart of {@link #executeOperationWithResponse(McpCallContext)}.
     */
    default CompletableFuture<String> executeOperationWithJsonResponse(McpCallContext context) {
        return McpJson.map(executeOperationWithResponse(context), McpJson::serialize);
    }

    /**
     * Sends a message that does not expect a response from the server - either a
     * client-initiated notification or a response to a server-initiated request.
     */
    void executeOperationWithoutResponse(McpClientMessage request);

    /**
     * Sends a message that does not expect a response from the server - either a
     * client-initiated notification or a response to a server-initiated request.
     */
    void executeOperationWithoutResponse(McpCallContext context);

    /**
     * Performs transport-specific health checks, if applicable. This is called
     * by `McpClient.checkHealth()` as the first check before performing a check
     * by sending a 'ping' over the MCP protocol. The purpose is that the
     * transport may have some specific and faster ways to detect that it is broken,
     * like for example, the STDIO transport can fail the check if it detects
     * that the server subprocess isn't alive anymore.
     */
    void checkHealth();

    void onFailure(Runnable actionOnFailure);

    /**
     * Informs the transport whether the modern MCP protocol (2026-07-28 or later) is in use.
     * HTTP-based transports use this to switch between modern headers (MCP-Protocol-Version,
     * Mcp-Method, Mcp-Name) and legacy session management (Mcp-Session-Id).
     * Default implementation is a no-op (for transports like stdio/WebSocket that don't need it).
     */
    default void setModernProtocol(boolean modernProtocol) {}

    /**
     * Sets the protocol version string to be sent in the MCP-Protocol-Version HTTP header.
     * Only relevant for HTTP-based transports in modern protocol mode.
     * Default implementation is a no-op.
     */
    default void setProtocolVersion(String protocolVersion) {}

    /**
     * Returns whether this transport requires an explicit {@code notifications/cancelled}
     * message to cancel an in-progress request. Transports that use per-request SSE
     * streams (like Streamable HTTP) cancel by closing the stream and should return
     * {@code false}. Transports that share a single channel (like stdio) must return
     * {@code true} so the server knows which request to cancel.
     * <p>
     * Note: when using the legacy protocol (2025-11-25), the client always sends
     * {@code notifications/cancelled} regardless of this value, because the legacy
     * spec states that disconnection should not be interpreted as cancellation.
     * <p>
     * Defaults to {@code true}, which is the safe answer for a transport that shares a
     * single channel; per-request-stream transports override it.
     */
    default boolean requiresCancellationNotification() {
        return true;
    }
}
