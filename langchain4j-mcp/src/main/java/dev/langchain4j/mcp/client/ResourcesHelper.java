package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.transport.McpJson;
import dev.langchain4j.mcp.protocol.McpListResourceTemplatesResult;
import dev.langchain4j.mcp.protocol.McpListResourcesResult;
import dev.langchain4j.mcp.protocol.McpReadResourceResponse;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ResourcesHelper {

    private static final Logger log = LoggerFactory.getLogger(ResourcesHelper.class);

    static List<McpResource> parseResourceRefs(JsonNode mcpMessage) {
        McpErrorHelper.checkForErrors(mcpMessage);
        McpListResourcesResult.Result result =
                McpJson.deserialize(mcpMessage, McpListResourcesResult.class).getResult();
        requireResult(result, mcpMessage);
        return require(result.getResources(), "resources", mcpMessage);
    }

    static List<McpResourceTemplate> parseResourceTemplateRefs(JsonNode mcpMessage) {
        McpErrorHelper.checkForErrors(mcpMessage);
        McpListResourceTemplatesResult.Result result =
                McpJson.deserialize(mcpMessage, McpListResourceTemplatesResult.class).getResult();
        requireResult(result, mcpMessage);
        return require(result.getResourceTemplates(), "resourceTemplates", mcpMessage);
    }

    static McpReadResourceResult parseResourceContents(JsonNode mcpMessage) {
        McpErrorHelper.checkForErrors(mcpMessage);
        McpReadResourceResponse.Result result =
                McpJson.deserialize(mcpMessage, McpReadResourceResponse.class).getResult();

        requireResult(result, mcpMessage);
        List<McpReadResourceResponse.Contents> contents = require(result.getContents(), "contents", mcpMessage);

        List<McpResourceContents> resourceContentsList = new ArrayList<>();
        for (McpReadResourceResponse.Contents item : contents) {
            if (item.getText() != null) {
                resourceContentsList.add(
                        new McpTextResourceContents(item.getUri(), item.getText(), item.getMimeType()));
            } else if (item.getBlob() != null) {
                resourceContentsList.add(
                        new McpBlobResourceContents(item.getUri(), item.getBlob(), item.getMimeType()));
            }
        }
        return new McpReadResourceResult(resourceContentsList);
    }

    private static void requireResult(Object result, JsonNode mcpMessage) {
        if (result == null) {
            log.warn("Result does not contain 'result' element: {}", mcpMessage);
            throw new IllegalResponseException("Result does not contain 'result' element");
        }
    }

    private static <T> List<T> require(List<T> values, String element, JsonNode mcpMessage) {
        if (values == null) {
            log.warn("Result does not contain '{}' element: {}", element, mcpMessage);
            throw new IllegalResponseException("Result does not contain '" + element + "' element");
        }
        return values;
    }
}
