package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;

class McpErrorHelper {

    static void checkForErrors(JsonNode mcpMessage) {
        if (mcpMessage.has("error")) {
            JsonNode errorNode = mcpMessage.get("error");
            throw new McpException(
                    errorNode.get("code").asInt(), errorNode.get("message").asText());
        }
    }
}
