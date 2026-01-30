package dev.langchain4j.mcp.client;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.List;
import java.util.Map;

/**
 * Represents a client that can communicate with an MCP server over a given transport protocol,
 * retrieve and execute tools using the server.
 */
public interface McpClient extends AutoCloseable {

    /**
     * Returns the unique key of this client.
     */
    String key();

    /**
     * Obtains a list of tools from the MCP server.
     */
    List<ToolSpecification> listTools();

    /**
     * Obtains a list of tools from the MCP server.
     */
    List<ToolSpecification> listTools(InvocationContext invocationContext);

    /**
     * Executes a tool on the MCP server and returns the result.
     * Currently, this expects a tool execution to only contain text-based results or JSON structured content.
     */
    ToolExecutionResult executeTool(ToolExecutionRequest executionRequest);

    /**
     * Executes a tool on the MCP server and returns the result.
     * Currently, this expects a tool execution to only contain text-based results or JSON structured content.
     */
    ToolExecutionResult executeTool(ToolExecutionRequest executionRequest, InvocationContext invocationContext);

    /**
     * Obtains the current list of resources available on the MCP server.
     */
    List<McpResource> listResources();

    /**
     * Obtains the current list of resources available on the MCP server.
     */
    List<McpResource> listResources(InvocationContext invocationContext);

    /**
     * Obtains the current list of resource templates (dynamic resources) available on the MCP server.
     */
    List<McpResourceTemplate> listResourceTemplates();

    /**
     * Obtains the current list of resource templates (dynamic resources) available on the MCP server.
     */
    List<McpResourceTemplate> listResourceTemplates(InvocationContext invocationContext);

    /**
     * Retrieves the contents of the resource with the specified URI. This also
     * works for dynamic resources (templates).
     */
    McpReadResourceResult readResource(String uri);

    /**
     * Retrieves the contents of the resource with the specified URI. This also
     * works for dynamic resources (templates).
     */
    McpReadResourceResult readResource(String uri, InvocationContext invocationContext);

    /**
     * Obtain a list of prompts available on the MCP server.
     */
    List<McpPrompt> listPrompts();

    /**
     * Render the contents of a prompt.
     */
    McpGetPromptResult getPrompt(String name, Map<String, Object> arguments);

    /**
     * Performs a health check that returns normally if the MCP server is reachable and
     * properly responding to ping requests. If this method throws an exception,
     * the health of this MCP client is considered degraded.
     */
    void checkHealth();

    /**
     * Sets the roots that are made available to the server upon its request.
     * After calling this method, the client also sends a `notifications/roots/list_changed` message to the server.
     */
    void setRoots(List<McpRoot> roots);
}
