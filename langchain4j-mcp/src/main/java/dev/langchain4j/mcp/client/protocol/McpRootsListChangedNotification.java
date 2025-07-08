package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;

@Internal
public class McpRootsListChangedNotification extends McpClientMessage {

    @JsonInclude
    public final McpClientMethod method = McpClientMethod.NOTIFICATION_ROOTS_LIST_CHANGED;

    public McpRootsListChangedNotification() {
        super(null);
    }
}
