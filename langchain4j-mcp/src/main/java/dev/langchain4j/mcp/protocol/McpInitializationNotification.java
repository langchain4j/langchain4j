package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;

@Internal
public class McpInitializationNotification extends McpJsonRpcMessage {

    @JsonInclude
    public final McpClientMethod method = McpClientMethod.NOTIFICATION_INITIALIZED;

    public McpInitializationNotification() {
        super(null);
    }
}
