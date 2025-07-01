package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

public class RootListChangedNotification extends McpClientMessage {

    @JsonInclude
    public final ClientMethod method = ClientMethod.NOTIFICATION_ROOT_LIST_CHANGED;

    public RootListChangedNotification() {
        super(null);
    }
}
