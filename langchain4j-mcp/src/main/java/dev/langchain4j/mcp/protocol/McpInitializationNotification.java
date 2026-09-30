package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code InitializedNotification} type from the MCP schema.
 * Only used with the legacy MCP protocol (versions up to 2025-11-25).
 */
@Internal
public class McpInitializationNotification extends McpClientNotification {

    public McpInitializationNotification() {
        super(McpClientMethod.NOTIFICATION_INITIALIZED);
    }
}
