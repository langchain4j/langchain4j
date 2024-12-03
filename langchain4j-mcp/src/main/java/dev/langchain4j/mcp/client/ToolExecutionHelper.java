package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

class ToolExecutionHelper {

    /**
     * Extracts a single string as a result from the 'content' element of
     * a CallToolResult message. Only supports String contents, if it finds any
     * other content types, it throws an exception.
     */
    static String extractResult(ArrayNode contents) {
        StringBuilder result = new StringBuilder();
        for (JsonNode content : contents) {
            String contentType = content.get("type").asText();
            if (contentType.equals("text")) {
                result.append(content.get("text").asText()).append("\n");
            } else {
                throw new RuntimeException("Unsupported content type: " + contentType);
            }
        }
        return result.toString();
    }
}
