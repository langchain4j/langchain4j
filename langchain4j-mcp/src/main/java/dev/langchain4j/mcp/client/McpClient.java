package dev.langchain4j.mcp.client;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.List;

/**
 * Represents a client that can communicate with an MCP server over a given transport protocol,
 * retrieve and execute tools using the server.
 */
public interface McpClient extends AutoCloseable {

    /**
     * Obtains a list of tools from the MCP server.
     */
    List<ToolSpecification> listTools();

    /**
     * Executes a tool on the MCP server and returns the result as a String.
     * Currently, this expects a tool execution to only contain text-based results.
     */
    String executeTool(ToolExecutionRequest executionRequest);
}
