package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;

@Internal
public class McpPingRequest extends McpJsonRpcMessage {

    @JsonInclude
    public final McpClientMethod method = McpClientMethod.PING;

    public McpPingRequest(Long id) {
        super(id);
    }
}
