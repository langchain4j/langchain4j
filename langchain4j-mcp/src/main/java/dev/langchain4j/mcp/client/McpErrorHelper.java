package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;

public class McpErrorHelper {

    static void checkForErrors(JsonNode mcpMessage) {
        if (mcpMessage.has("error")) {
            JsonNode errorNode = mcpMessage.get("error");
            McpError error = DefaultMcpClient.OBJECT_MAPPER.convertValue(errorNode, McpError.class);
            throw new McpException(error);
        }
    }
}
