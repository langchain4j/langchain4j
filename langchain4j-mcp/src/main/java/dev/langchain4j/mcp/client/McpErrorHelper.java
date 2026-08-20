package dev.langchain4j.mcp.client;

import dev.langchain4j.mcp.protocol.McpErrorResponse;
import dev.langchain4j.mcp.client.transport.McpRawJson;
import com.fasterxml.jackson.databind.JsonNode;

class McpErrorHelper {

    static void checkForErrors(JsonNode mcpMessage) {
        McpErrorResponse.Error error =
                McpRawJson.deserialize(mcpMessage, McpErrorResponse.class).getError();
        if (error != null) {
            throw new McpException(error.getCode(), error.getMessage());
        }
    }
}
