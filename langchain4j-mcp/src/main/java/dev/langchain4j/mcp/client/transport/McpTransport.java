package dev.langchain4j.mcp.client.transport;

import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

/**
 * The transport contract between the MCP client and a server connection.
 *
 * <p>Responses cross this interface as unparsed JSON text, so that a transport does not need a JSON
 * library. A transport registers each request it sends with
 * {@link McpOperationHandler#expectResponse(Long, CompletableFuture)} and passes every message it
 * receives to {@link McpOperationHandler#onMessage(String)}.
 *
 * <p>Besides the lifecycle methods, a transport implements three methods:
 * {@link #sendInitializeRequest(McpInitializeRequest)}, {@link #sendRequest(McpCallContext)} and
 * {@link #sendMessage(McpCallContext)}. The overloads that take a bare {@link McpClientMessage}
 * wrap it in a {@link McpCallContext} without per-call headers or {@code _meta}.
 */
public interface McpTransport extends Closeable {

    /**
     * Creates a connection to the MCP server (runs the server as a subprocess if needed).
     * This does NOT yet send the "initialize" message to negotiate capabilities.
     */
    void start(McpOperationHandler messageHandler);

    /**
     * Sends the MCP {@code InitializeRequest} and returns the server's response as unparsed JSON
     * text, so that a transport does not need a JSON library. This has to be called AFTER the
     * "start" method; when it returns successfully, the transport is ready to be used. Only used
     * with the legacy MCP protocol (versions up to 2025-11-25); the modern protocol uses
     * {@code server/discover}.
     */
    CompletableFuture<String> sendInitializeRequest(McpInitializeRequest request);

    /**
     * Sends a JSON-RPC request and returns the server's response as unparsed JSON text. The context
     * carries the message and its per-call headers and {@code _meta}, which the transport applies.
     */
    CompletableFuture<String> sendRequest(McpCallContext context);

    /**
     * Sends a JSON-RPC request that carries no per-call headers or {@code _meta}, and returns the
     * server's response as unparsed JSON text.
     */
    default CompletableFuture<String> sendRequest(McpClientMessage request) {
        return sendRequest(new McpCallContext(null, request));
    }

    /**
     * Sends a message that does not expect a reply - in MCP terms a notification, or a
     * response to a server-initiated request. The context carries the message and its per-call
     * headers and {@code _meta}, which the transport applies.
     */
    void sendMessage(McpCallContext context);

    /**
     * Sends a message that does not expect a reply and carries no per-call headers or
     * {@code _meta}.
     */
    default void sendMessage(McpClientMessage request) {
        sendMessage(new McpCallContext(null, request));
    }

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
