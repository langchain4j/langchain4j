package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.langchain4j.Internal;

/**
 * Enum representing method names for server-initiated MCP messages.
 */
@Internal
public enum McpServerMethod {
    PING("ping"),
    ROOTS_LIST("roots/list"),
    NOTIFICATION_MESSAGE("notifications/message"),
    NOTIFICATION_TOOLS_LIST_CHANGED("notifications/tools/list_changed"),
    NOTIFICATION_RESOURCES_LIST_CHANGED("notifications/resources/list_changed"),
    NOTIFICATION_PROMPTS_LIST_CHANGED("notifications/prompts/list_changed"),
    NOTIFICATION_PROGRESS("notifications/progress");

    private final String value;

    McpServerMethod(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static McpServerMethod from(String value) {
        for (McpServerMethod method : values()) {
            if (method.value.equals(value)) {
                return method;
            }
        }
        return null;
    }
}
