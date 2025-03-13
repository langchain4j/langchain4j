package dev.langchain4j.mcp.client;

import static dev.langchain4j.mcp.client.DefaultMcpClient.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ResourcesHelper {

    private static final Logger log = LoggerFactory.getLogger(ResourcesHelper.class);

    static List<ResourceRef> parseResourceRefs(JsonNode mcpMessage) {
        if (mcpMessage.has("result")) {
            JsonNode resultNode = mcpMessage.get("result");
            if (resultNode.has("resources")) {
                List<ResourceRef> resourceRefs = new ArrayList<>();
                for (JsonNode resourceNode : resultNode.get("resources")) {
                    resourceRefs.add(OBJECT_MAPPER.convertValue(resourceNode, ResourceRef.class));
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

    public static ResourceResponse parseResourceContents(JsonNode mcpMessage) {
        if (mcpMessage.has("result")) {
            JsonNode resultNode = mcpMessage.get("result");
            if (resultNode.has("contents")) {
                List<ResourceContents> resourceContentsList = new ArrayList<>();
                for (JsonNode resourceNode : resultNode.get("contents")) {
                    String uri = resourceNode.get("uri").asText();
                    String mimeType = resourceNode.get("mimeType") != null
                            ? resourceNode.get("mimeType").asText()
                            : null;
                    if (resourceNode.has("text")) {
                        resourceContentsList.add(new TextResourceContents(
                                uri, resourceNode.get("text").asText(), mimeType));
                    } else if (resourceNode.has("blob")) {
                        resourceContentsList.add(new BlobResourceContents(
                                uri, resourceNode.get("blob").asText(), mimeType));
                    }
                }
                return new ResourceResponse(resourceContentsList);
            } else {
                log.warn("Result does not contain 'contents' element: {}", resultNode);
                throw new IllegalResponseException("Result does not contain 'resources' element");
            }
        } else {
            log.warn("Result does not contain 'result' element: {}", mcpMessage);
            throw new IllegalResponseException("Result does not contain 'result' element");
        }
    }

    public static List<ResourceTemplateRef> parseResourceTemplateRefs(JsonNode mcpMessage) {
        if (mcpMessage.has("result")) {
            JsonNode resultNode = mcpMessage.get("result");
            if (resultNode.has("resourceTemplates")) {
                List<ResourceTemplateRef> resourceTemplateRefs = new ArrayList<>();
                for (JsonNode resourceTemplateNode : resultNode.get("resourceTemplates")) {
                    resourceTemplateRefs.add(
                            OBJECT_MAPPER.convertValue(resourceTemplateNode, ResourceTemplateRef.class));
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
