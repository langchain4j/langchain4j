package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code JSONRPCResponse} type from the MCP schema.
 */
@Internal
public class McpClientResponse extends McpClientMessage {

    /**
     * The id of the request being answered, exactly as the server sent it. MCP allows it to be a
     * string or a number, and the response has to carry the same value.
     */
    private final Object requestId;

    public McpClientResponse(Long id) {
        super(id, null);
        this.requestId = id;
    }

    /**
     * @param requestId the id of the request being answered, exactly as the server sent it (a string or a number)
     */
    public McpClientResponse(Object requestId) {
        super(requestId instanceof Number ? ((Number) requestId).longValue() : null, null);
        this.requestId = requestId;
    }

    /**
     * Returns the id as a number, or {@code null} if the server used a string id. The id that is written to
     * the wire is {@link #getRequestId()}.
     */
    @Override
    @JsonIgnore
    public Long getId() {
        return super.getId();
    }

    @JsonProperty("id")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object getRequestId() {
        return requestId;
    }
}
