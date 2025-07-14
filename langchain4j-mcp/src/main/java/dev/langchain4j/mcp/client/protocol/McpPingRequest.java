package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;

@Internal
public class McpPingRequest extends McpClientMessage {

    @JsonInclude
    public final McpClientMethod method = McpClientMethod.PING;

    public McpPingRequest(Long id) {
        super(id);
    }
}
