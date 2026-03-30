package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code JSONRPCNotification} type from the MCP schema.
 */
@Internal
public class McpClientNotification extends McpClientMessage {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private McpClientParams params;

    public McpClientNotification(McpClientMethod method) {
        super(null, method);
    }

    public McpClientParams getParams() {
        return params;
    }

    public void setParams(McpClientParams params) {
        this.params = params;
    }
}
