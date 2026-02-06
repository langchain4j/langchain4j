package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

@Internal
public class McpInitializeRequest extends McpClientMessage {

    private McpInitializeParams params;

    public McpInitializeRequest(Long id) {
        super(id, McpClientMethod.INITIALIZE);
    }

    public McpInitializeParams getParams() {
        return params;
    }

    public void setParams(final McpInitializeParams params) {
        this.params = params;
    }
}
