package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;

@Internal
public class McpInitializeRequest extends McpClientMessage {

    @JsonInclude
    public final McpClientMethod method = McpClientMethod.INITIALIZE;

    private McpInitializeParams params;

    public McpInitializeRequest(final Long id) {
        super(id);
    }

    public McpInitializeParams getParams() {
        return params;
    }

    public void setParams(final McpInitializeParams params) {
        this.params = params;
    }
}
