package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

@Internal
public class McpPingRequest extends McpClientMessage {

    public McpPingRequest(Long id) {
        super(id, McpClientMethod.PING);
    }
}
