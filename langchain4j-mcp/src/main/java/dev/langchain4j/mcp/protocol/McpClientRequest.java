package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code JSONRPCRequest} type from the MCP schema.
 */
@Internal
public class McpClientRequest extends McpClientMessage {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private McpClientParams params;

    public McpClientRequest(Long id, McpClientMethod method) {
        super(id, method);
    }

    public McpClientParams getParams() {
        return params;
    }

    public void setParams(McpClientParams params) {
        this.params = params;
    }
}
