package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

public class McpInitializeRequest extends McpClientMessage {

    @JsonInclude
    public final ClientMethod method = ClientMethod.INITIALIZE;

    private InitializeParams params;

    public McpInitializeRequest(final Long id) {
        super(id);
    }

    public InitializeParams getParams() {
        return params;
    }

    public void setParams(final InitializeParams params) {
        this.params = params;
    }
}
