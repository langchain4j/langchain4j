package dev.langchain4j.mcp.client;

import dev.langchain4j.mcp.client.transport.McpJson;
import dev.langchain4j.mcp.protocol.McpErrorResponse;

class McpErrorHelper {

    static void checkForErrors(String mcpMessage) {
        if (mcpMessage == null || !mcpMessage.contains("\"error\"")) {
            // the common case: skip parsing the whole response just to find no error. A false
            // positive here only costs the parse below, which then finds no error object.
            return;
        }
        McpErrorResponse.Error error =
                McpJson.deserialize(mcpMessage, McpErrorResponse.class).getError();
        if (error != null) {
            throw new McpException(error.getCode(), error.getMessage());
        }
    }
}
