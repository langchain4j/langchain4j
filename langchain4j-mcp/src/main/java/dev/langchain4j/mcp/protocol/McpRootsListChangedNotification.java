package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code RootsListChangedNotification} type from the MCP schema.
 */
@Internal
public class McpRootsListChangedNotification extends McpClientNotification {

    public McpRootsListChangedNotification() {
        super(McpClientMethod.NOTIFICATION_ROOTS_LIST_CHANGED);
    }
}
