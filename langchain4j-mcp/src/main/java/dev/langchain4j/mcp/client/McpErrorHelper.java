package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.transport.McpJson;
import dev.langchain4j.mcp.protocol.McpErrorResponse;

class McpErrorHelper {

    static void checkForErrors(JsonNode mcpMessage) {
        if (!mcpMessage.has("error")) {
            // the common case: skip building the whole response just to find no error
            return;
        }
        McpErrorResponse.Error error =
                McpJson.deserialize(mcpMessage, McpErrorResponse.class).getError();
        if (error != null) {
            throw new McpException(error.getCode(), error.getMessage());
        }
    }
}
