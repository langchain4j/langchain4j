package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

public class McpClientMessage {

    @JsonInclude
    public final String jsonrpc = "2.0";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long id;

    public McpClientMessage(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }
}
