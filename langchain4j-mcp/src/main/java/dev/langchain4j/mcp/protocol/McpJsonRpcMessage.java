package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;

@Internal
public class McpJsonRpcMessage {

    @JsonInclude
    public final String jsonrpc = "2.0";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long id;

    public McpJsonRpcMessage(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }
}
