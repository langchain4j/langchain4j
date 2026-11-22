package dev.langchain4j.mcp.client.logging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.mcp.client.transport.McpJson;
import java.util.Map;
import java.util.Objects;

public class McpLogMessage {

    private final McpLogLevel level;
    private final String logger;
    private final Object data;

    @JsonCreator
    public McpLogMessage(
            @JsonProperty("level") McpLogLevel level,
            @JsonProperty("logger") String logger,
            @JsonProperty("data") Object data
    ) {
        this.level = level;
        this.logger = logger;
        this.data = data;
    }

    /**
     * Parses a McpLogMessage from the contents of the 'params' object inside a 'notifications/message'
     * message, presented as plain values.
     */
    public static McpLogMessage fromMap(Map<String, Object> params) {
        Object levelValue = params.get("level");
        McpLogLevel level = McpLogLevel.from(levelValue == null ? null : String.valueOf(levelValue));
        Object logger = params.get("logger");
        return new McpLogMessage(level, logger == null ? null : String.valueOf(logger), params.get("data"));
    }

    public McpLogLevel level() {
        return level;
    }

    public String logger() {
        return logger;
    }

    /**
     * Returns the log payload as JSON text.
     */
    public String dataAsJson() {
        return data == null ? null : McpJson.serialize(data);
    }

    /**
     * Returns the log payload as a plain map, so consumers do not need a JSON library.
     *
     * @return null when the payload is not a JSON object. MCP allows any JSON-serializable value
     * here and a plain string is common, so use {@link #dataAsObject()} to read those.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> dataAsMap() {
        return data instanceof Map ? (Map<String, Object>) data : null;
    }

    /**
     * Returns the log payload as a plain JDK value: a {@link String}, a {@link Map}, a
     * {@link java.util.List}, a boxed primitive, or null. MCP defines the payload as any
     * JSON-serializable value, so this is the accessor that can represent all of them.
     */
    public Object dataAsObject() {
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
