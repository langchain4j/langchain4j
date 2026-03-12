package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;

@Internal
public class McpClientMessage extends McpJsonRpcMessage {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public final McpClientMethod method;

    public McpClientMessage(Long id, McpClientMethod method) {
        super(id);
        this.method = method;
    }
}
