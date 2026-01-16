package dev.langchain4j.mcp.client;

import static dev.langchain4j.mcp.client.DefaultMcpClient.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PromptsHelper {

    private static final Logger log = LoggerFactory.getLogger(PromptsHelper.class);

    static List<McpPrompt> parsePromptRefs(JsonNode mcpMessage) {
        McpErrorHelper.checkForErrors(mcpMessage);
        if (mcpMessage.has("result")) {
            JsonNode resultNode = mcpMessage.get("result");
            if (resultNode.has("prompts")) {
                List<McpPrompt> promptRefs = new ArrayList<>();
                for (JsonNode promptNode : resultNode.get("prompts")) {
                    promptRefs.add(OBJECT_MAPPER.convertValue(promptNode, McpPrompt.class));
                }
                return promptRefs;
            } else {
                log.warn("Result does not contain 'prompts' element: {}", resultNode);
                throw new IllegalResponseException("Result does not contain 'prompts' element");
            }
        } else {
            log.warn("Result does not contain 'result' element: {}", mcpMessage);
            throw new IllegalResponseException("Result does not contain 'result' element");
        }
    }

    static McpGetPromptResult parsePromptContents(JsonNode mcpMessage) {
        McpErrorHelper.checkForErrors(mcpMessage);
        return OBJECT_MAPPER.convertValue(mcpMessage.get("result"), McpGetPromptResult.class);
    }
}
