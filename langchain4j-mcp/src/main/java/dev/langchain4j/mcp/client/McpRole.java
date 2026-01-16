package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * The 'Role' object from the MCP protocol schema.
 */
public enum McpRole {
    ASSISTANT,
    USER;

    // to allow case-insensitive deserialization
    @JsonCreator
    public static McpRole fromString(String key) {
        for (McpRole role : McpRole.values()) {
            if (role.name().equalsIgnoreCase(key)) {
                return role;
            }
        }
        return null;
    }
}
