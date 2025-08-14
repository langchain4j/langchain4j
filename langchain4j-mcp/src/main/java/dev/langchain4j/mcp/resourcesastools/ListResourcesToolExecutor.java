package dev.langchain4j.mcp.resourcesastools;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.internal.Json;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.ArrayList;
import java.util.List;

/**
 * Default Executor for the 'list_resources' synthetic tool that can retrieve a list of resources from one or more MCP servers
 * and returns a JSON representation of the available resources.
 */
class ListResourcesToolExecutor implements ToolExecutor { // TODO

    private final List<McpClient> mcpClients;

    ListResourcesToolExecutor(List<McpClient> mcpClients) {
        this.mcpClients = mcpClients;
    }

    @Override
    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        List<ResourceDescription> descriptions = new ArrayList<>();
        for (McpClient client : mcpClients) {
            for (McpResource resource : client.listResources()) {
                descriptions.add(new ResourceDescription(
                        client.key(),
                        resource.uri(),
                        null,
                        resource.name(),
                        resource.description(),
                        resource.mimeType()));
            }
            for (McpResourceTemplate template : client.listResourceTemplates()) {
                descriptions.add(new ResourceDescription(
                        client.key(),
                        null,
                        template.uriTemplate(),
                        template.name(),
                        template.description(),
                        template.mimeType()));
            }
        }
        return Json.toJson(descriptions);
    }

    private static class ResourceDescription {

        ResourceDescription(
                String mcpServer, String uri, String uriTemplate, String name, String description, String mimeType) {
            this.mcpServer = mcpServer;
            this.uri = uri;
            this.uriTemplate = uriTemplate;
            this.name = name;
            this.description = description;
            this.mimeType = mimeType;
        }

        String mcpServer;
        String uri;
        String uriTemplate;
        String name;
        String description;
        String mimeType;
    }
}
