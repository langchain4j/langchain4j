package dev.langchain4j.mcp.client.transport;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.protocol.McpClientMessage;
import dev.langchain4j.mcp.client.protocol.McpInitializeRequest;
import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

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
     */
    CompletableFuture<JsonNode> initialize(McpInitializeRequest request);

    /**
     * Executes an operation that expects a response from the server.
     */
    CompletableFuture<JsonNode> executeOperationWithResponse(McpClientMessage request);

    /**
     * Sends a message that does not expect a response from the server. The 'id' field
     * of the message should be null.
     */
    void executeOperationWithoutResponse(McpClientMessage request);
}
