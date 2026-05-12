package dev.langchain4j.mcp.client;

import static dev.langchain4j.mcp.McpFields.*;
import static dev.langchain4j.mcp.client.DefaultMcpClient.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to parse MCP responses into {@link McpPrompt} and
 * {@link McpGetPromptResult} objects.
 */
class PromptsHelper {

    private static final Logger log = LoggerFactory.getLogger(PromptsHelper.class);

    /**
     * Parses a list of prompts from the MCP message JSON.
     *
     * <p>
     * Example response that this method can parse:
     *
     * <pre>{@code
     * {
     *   "jsonrpc": "2.0",
     *   "id": 1,
     *   "result": {
     *     "prompts": [
     *       {
     *         "name": "code_review",
     *         "title": "Request Code Review",
     *         "description": "Asks the LLM to analyze code quality and suggest improvements",
     *         "arguments": [
     *           {
     *             "name": "code",
     *             "description": "The code to review",
     *             "required": true
     *           }
     *         ],
     *         "icons": [
     *           {
     *             "src": "https://example.com/review-icon.svg",
     *             "mimeType": "image/svg+xml",
     *             "sizes": ["any"]
     *           }
     *         ]
     *       }
     *     ],
     *     "nextCursor": "next-page-cursor"
     *   }
     * }
     * }</pre>
     *
     * @param mcpMessage The MCP response JSON node
     * @return list of {@link McpPrompt} parsed from the response
     * @throws IllegalResponseException if 'result' or 'prompts' fields are missing
     */
    static List<McpPrompt> parsePromptRefs(JsonNode mcpMessage) {
        McpErrorHelper.checkForErrors(mcpMessage);

        JsonNode resultNode = mcpMessage.get(RESULT_FIELD);
        if (resultNode == null) {
            log.warn("Result does not contain '{}': {}", RESULT_FIELD, mcpMessage);
            throw new IllegalResponseException(String.format("Result does not contain '%s' element", RESULT_FIELD));
        }

        JsonNode promptsNode = resultNode.get(PROMPTS_FIELD);
        if (promptsNode == null) {
            log.warn("Result does not contain '{}': {}", PROMPTS_FIELD, resultNode);
            throw new IllegalResponseException(String.format("Result does not contain '%s' element", PROMPTS_FIELD));
        }

        List<McpPrompt> promptRefs = new ArrayList<>();
        promptsNode.forEach(node -> promptRefs.add(OBJECT_MAPPER.convertValue(node, McpPrompt.class)));
        return promptRefs;
    }

    static McpGetPromptResult parsePromptContents(JsonNode mcpMessage) {
        McpErrorHelper.checkForErrors(mcpMessage);
        return OBJECT_MAPPER.convertValue(mcpMessage.get(RESULT_FIELD), McpGetPromptResult.class);
    }
}
