package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.transport.McpJson;
import dev.langchain4j.mcp.protocol.McpGetPromptResponse;
import dev.langchain4j.mcp.protocol.McpListPromptsResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PromptsHelper {

    private static final Logger log = LoggerFactory.getLogger(PromptsHelper.class);

    static List<McpPrompt> parsePromptRefs(JsonNode mcpMessage) {
        McpErrorHelper.checkForErrors(mcpMessage);
        McpListPromptsResult.Result result =
                McpJson.deserialize(mcpMessage, McpListPromptsResult.class).getResult();
        if (result == null) {
            log.warn("Result does not contain 'result' element: {}", mcpMessage);
            throw new IllegalResponseException("Result does not contain 'result' element");
        }
        if (result.getPrompts() == null) {
            log.warn("Result does not contain 'prompts' element: {}", mcpMessage);
            throw new IllegalResponseException("Result does not contain 'prompts' element");
        }
        return result.getPrompts();
    }

    static McpGetPromptResult parsePromptContents(JsonNode mcpMessage) {
        McpErrorHelper.checkForErrors(mcpMessage);
        return McpJson.deserialize(mcpMessage, McpGetPromptResponse.class).getResult();
    }
}
