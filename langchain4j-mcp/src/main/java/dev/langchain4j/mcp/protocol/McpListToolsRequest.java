package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code ListToolsRequest} type from the MCP schema.
 */
@Internal
public class McpListToolsRequest extends McpClientRequest {

    public McpListToolsRequest(Long id) {
        super(id, McpClientMethod.TOOLS_LIST);
    }

    @JsonIgnore
    public void setCursor(String cursor) {
        McpListToolsParams p = new McpListToolsParams();
        p.setCursor(cursor);
        setParams(p);
    }
}
