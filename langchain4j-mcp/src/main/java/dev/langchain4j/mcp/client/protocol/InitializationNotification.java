package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

public class InitializationNotification extends McpClientMessage {

    @JsonInclude
    public final ClientMethod method = ClientMethod.NOTIFICATION_INITIALIZED;

    public InitializationNotification() {
        super(null);
    }
}
