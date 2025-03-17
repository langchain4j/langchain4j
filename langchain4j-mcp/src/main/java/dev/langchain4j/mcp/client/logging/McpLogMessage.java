package dev.langchain4j.mcp.client.logging;

import com.fasterxml.jackson.databind.JsonNode;

public record McpLogMessage(McpLogLevel level, String logger, JsonNode data) {

    /**
     * Parses a McpLogMessage from the contents of the 'params' object inside a 'notifications/message' message.
     */
    public static McpLogMessage fromJson(JsonNode json) {
        McpLogLevel level = McpLogLevel.from(json.get("level").asText());
        String logger = json.get("logger").asText();
        JsonNode data = json.get("data");
        return new McpLogMessage(level, logger, data);
    }
}
