package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

@Internal
public class McpInitializationNotification extends McpClientMessage {

    public McpInitializationNotification() {
        super(null, McpClientMethod.NOTIFICATION_INITIALIZED);
    }
}
