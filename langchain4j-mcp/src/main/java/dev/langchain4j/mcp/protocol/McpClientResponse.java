package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code JSONRPCResponse} type from the MCP schema.
 * <p>
 * The ID is the one of the server request being responded to, and it is sent back exactly as the server sent it,
 * because a JSON-RPC ID can be a string as well as a number.
 */
@Internal
public class McpClientResponse extends McpClientMessage {

    private final Object requestId;

    public McpClientResponse(Long id) {
        this((Object) id);
    }

    public McpClientResponse(Object id) {
        super(null, null);
        this.requestId = id;
    }

    @Override
    @JsonIgnore
    public Long getId() {
        return requestId instanceof Number number ? number.longValue() : null;
    }

    @JsonProperty("id")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object getRequestId() {
        return requestId;
    }
}
