package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

@Internal
public class McpRootsListChangedNotification extends McpClientMessage {

    public McpRootsListChangedNotification() {
        super(null, McpClientMethod.NOTIFICATION_ROOTS_LIST_CHANGED);
    }
}
