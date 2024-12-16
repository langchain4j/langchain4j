package dev.langchain4j.mcp.client.transport;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.client.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.client.protocol.McpListToolsRequest;
import java.io.Closeable;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

public interface McpTransport extends Closeable {

    /**
     * Creates a connection to the MCP server (runs the server as a subprocess if needed).
     * This does NOT yet send the "initialize" message to negotiate capabilities.
     */
    void start();

    /**
     * Sends the "initialize" message to the MCP server to negotiate
     * capabilities, supported protocol version etc. When this method
     * returns successfully, the transport is fully initialized and ready to
     * be used. This has to be called AFTER the "start" method.
     */
    JsonNode initialize(McpInitializeRequest request);

    /**
     * Requests a list of available tools from the MCP server.
     */
    JsonNode listTools(McpListToolsRequest request);

    /**
     * Executes a tool on the MCP server.
     * @param request the tool execution request
     * @param timeout the maximum time to wait for the tool to complete
     * @throws TimeoutException if the tool execution times out (in this case, the transport should also send a CancellationNotification to the server)
     */
    JsonNode executeTool(McpCallToolRequest request, Duration timeout) throws TimeoutException;
}
