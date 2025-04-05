package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

public class PingRequest extends McpClientMessage {

    @JsonInclude
    public final ClientMethod method = ClientMethod.PING;

    public PingRequest(Long id) {
        super(id);
    }
}
