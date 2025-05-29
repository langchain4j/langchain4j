package dev.langchain4j.mcp.client;

import static dev.langchain4j.mcp.client.DefaultMcpClient.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ResourcesHelper {

    private static final Logger log = LoggerFactory.getLogger(ResourcesHelper.class);

    static List<McpResource> parseResourceRefs(JsonNode mcpMessage) {
        McpErrorHelper.checkForErrors(mcpMessage);
        if (mcpMessage.has("result")) {
            JsonNode resultNode = mcpMessage.get("result");
            if (resultNode.has("resources")) {
                List<McpResource> resourceRefs = new ArrayList<>();
                for (JsonNode resourceNode : resultNode.get("resources")) {
                    resourceRefs.add(OBJECT_MAPPER.convertValue(resourceNode, McpResource.class));
                }
                return resourceRefs;
            } else {
                log.warn("Result does not contain 'resources' element: {}", resultNode);
                throw new IllegalResponseException("Result does not contain 'resources' element");
            }
        } else {
            log.warn("Result does not contain 'result' element: {}", mcpMessage);
            throw new IllegalResponseException("Result does not contain 'result' element");
        }
    }

    static McpReadResourceResult parseResourceContents(JsonNode mcpMessage) {
        McpErrorHelper.checkForErrors(mcpMessage);
        if (mcpMessage.has("result")) {
            JsonNode resultNode = mcpMessage.get("result");
            if (resultNode.has("contents")) {
                List<McpResourceContents> resourceContentsList = new ArrayList<>();
                for (JsonNode resourceNode : resultNode.get("contents")) {
                    String uri = resourceNode.get("uri").asText();
                    String mimeType = resourceNode.get("mimeType") != null
                            ? resourceNode.get("mimeType").asText()
                            : null;
                    if (resourceNode.has("text")) {
                        resourceContentsList.add(new McpTextResourceContents(
                                uri, resourceNode.get("text").asText(), mimeType));
                    } else if (resourceNode.has("blob")) {
                        resourceContentsList.add(new McpBlobResourceContents(
                                uri, resourceNode.get("blob").asText(), mimeType));
                    }
                }
                return new McpReadResourceResult(resourceContentsList);
            } else {
                log.warn("Result does not contain 'contents' element: {}", resultNode);
                throw new IllegalResponseException("Result does not contain 'resources' element");
            }
        } else {
            log.warn("Result does not contain 'result' element: {}", mcpMessage);
            throw new IllegalResponseException("Result does not contain 'result' element");
        }
    }

    static List<McpResourceTemplate> parseResourceTemplateRefs(JsonNode mcpMessage) {
        McpErrorHelper.checkForErrors(mcpMessage);
        if (mcpMessage.has("result")) {
            JsonNode resultNode = mcpMessage.get("result");
            if (resultNode.has("resourceTemplates")) {
                List<McpResourceTemplate> resourceTemplateRefs = new ArrayList<>();
                for (JsonNode resourceTemplateNode : resultNode.get("resourceTemplates")) {
                    resourceTemplateRefs.add(
                            OBJECT_MAPPER.convertValue(resourceTemplateNode, McpResourceTemplate.class));
                }
                return resourceTemplateRefs;
            } else {
                log.warn("Result does not contain 'resourceTemplates' element: {}", resultNode);
                throw new IllegalResponseException("Result does not contain 'resourceTemplates' element");
            }
        } else {
            log.warn("Result does not contain 'result' element: {}", mcpMessage);
            throw new IllegalResponseException("Result does not contain 'result' element");
        }
    }
}
