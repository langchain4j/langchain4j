package dev.langchain4j.mcp.client.logging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public class McpLogMessage {

    private final McpLogLevel level;
    private final String logger;
    private final JsonNode data;

    @JsonCreator
    public McpLogMessage(
            @JsonProperty("level") McpLogLevel level,
            @JsonProperty("logger") String logger,
            @JsonProperty("data") JsonNode data
    ) {
        this.level = level;
        this.logger = logger;
        this.data = data;
    }

    /**
     * Parses a McpLogMessage from the contents of the 'params' object inside a 'notifications/message' message.
     */
    public static McpLogMessage fromJson(JsonNode json) {
        McpLogLevel level = McpLogLevel.from(json.get("level").asText());
        JsonNode loggerNode = json.get("logger");
        String logger = loggerNode != null ? loggerNode.asText() : null;
        JsonNode data = json.get("data");
        return new McpLogMessage(level, logger, data);
    }

    public McpLogLevel level() {
        return level;
    }

    public String logger() {
        return logger;
    }

    public JsonNode data() {
        return data;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (McpLogMessage) obj;
        return Objects.equals(this.level, that.level) &&
                Objects.equals(this.logger, that.logger) &&
                Objects.equals(this.data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, logger, data);
    }

    @Override
    public String toString() {
        return "McpLogMessage[" +
                "level=" + level + ", " +
                "logger=" + logger + ", " +
                "data=" + data + ']';
    }
}
