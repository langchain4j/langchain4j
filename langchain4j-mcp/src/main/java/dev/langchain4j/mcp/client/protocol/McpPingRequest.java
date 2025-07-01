package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

public class McpPingRequest extends McpClientMessage {

    @JsonInclude
    public final ClientMethod method = ClientMethod.PING;

    public McpPingRequest(Long id) {
        super(id);
    }
}
