package dev.langchain4j.mcp.client.transport;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.client.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.client.protocol.McpListToolsRequest;
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
     * Requests a list of available tools from the MCP server.
     */
    CompletableFuture<JsonNode> listTools(McpListToolsRequest request);

    /**
     * Executes a tool on the MCP server.
     * @param request the tool execution request
     */
    CompletableFuture<JsonNode> executeTool(McpCallToolRequest request);

    /**
     * Cancels a running operation on the server (sends a 'notifications/cancelled' message to the server).
     * This does not expect any response from the server.
     * @param operationId The ID of the operation to be cancelled.
     */
    void cancelOperation(long operationId);
}
