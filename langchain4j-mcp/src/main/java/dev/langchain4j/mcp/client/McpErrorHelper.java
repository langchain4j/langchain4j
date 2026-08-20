package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.transport.McpRawJson;
import dev.langchain4j.mcp.protocol.McpErrorResponse;

class McpErrorHelper {

    static void checkForErrors(JsonNode mcpMessage) {
        McpErrorResponse.Error error =
                McpRawJson.deserialize(mcpMessage, McpErrorResponse.class).getError();
        if (error != null) {
            throw new McpException(error.getCode(), error.getMessage());
        }
    }
}
