package dev.langchain4j.mcp.resourcesastools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.List;

/**
 * A presenter that presents MCP resources (from one or more MCP clients) as tools to a chat model, allowing the
 * chat model to discover and interact with resources. An implementation has to provide two tools,
 * one for obtaining a list of resources, one for obtaining a particular resource. See {@link DefaultMcpResourcesAsToolsPresenter}
 * for the default implementation.
 */
public interface McpResourcesAsToolsPresenter {

    /**
     * Create a specification for the tool that lists available resources.
     */
    ToolSpecification createListResourcesSpecification();

    /**
     * Create an executor for the tool that lists available resources.
     */
    ToolExecutor createListResourcesExecutor(List<McpClient> mcpClients);

    /**
     * Create a specification for the tool that gets a particular resource.
     */
    ToolSpecification createGetResourceSpecification();

    /**
     * Create an executor for the tool that gets a particular resource.
     */
    ToolExecutor createGetResourceExecutor(List<McpClient> mcpClients);
}
