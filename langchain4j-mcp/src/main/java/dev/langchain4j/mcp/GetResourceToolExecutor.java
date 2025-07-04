package dev.langchain4j.mcp;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.internal.Json;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpResourceContents;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.List;
import java.util.Optional;

/**
 * Executor for the 'get_resource' synthetic tool that can retrieve a resource from an MCP server.
 */
class GetResourceToolExecutor implements ToolExecutor {

    private final List<McpClient> mcpClients;

    GetResourceToolExecutor(List<McpClient> mcpClients) {
        this.mcpClients = mcpClients;
    }

    @Override
    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        ObjectNode arguments = Json.fromJson(toolExecutionRequest.arguments(), ObjectNode.class);
        if (!arguments.has("mcpServer")) {
            return "ERROR: missing argument 'mcpServer'";
        }
        String mcpServerKey = arguments.get("mcpServer").asText();
        if (!arguments.has("uri")) {
            return "ERROR: missing argument 'uri'";
        }
        String uri = arguments.get("uri").asText();
        Optional<McpClient> client = mcpClients.stream()
                .filter(mcpClient -> mcpClient.key().equals(mcpServerKey))
                .findFirst();
        if (client.isEmpty()) {
            return "ERROR: unknown MCP server: " + mcpServerKey;
        } else {
            StringBuilder result = new StringBuilder();
            List<McpResourceContents> contents = client.get().readResource(uri).contents();
            for (McpResourceContents content : contents) {
                if (content instanceof McpTextResourceContents) {
                    result.append(((McpTextResourceContents) content).text());
                } else {
                    // TODO: handle binary resource
                }
            }
            return result.toString();
        }
    }

    private Optional<McpClient> findMcpClientByKey(String key) {
        return mcpClients.stream()
                .filter(mcpClient -> mcpClient.key().equals(key))
                .findFirst();
    }
}
