package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The icon theme from the MCP protocol schema.
 */
public enum McpIconTheme {
    DARK("dark"),
    LIGHT("light");

    private final String value;

    McpIconTheme(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static McpIconTheme from(String value) {
        for (McpIconTheme theme : values()) {
            if (theme.value.equals(value)) {
                return theme;
            }
        }
        return null;
    }
}
