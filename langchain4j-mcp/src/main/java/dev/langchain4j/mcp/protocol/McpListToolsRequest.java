package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code ListToolsRequest} type from the MCP schema.
 */
@Internal
public class McpListToolsRequest extends McpClientRequest {

    public McpListToolsRequest(Long id, String cursor) {
        super(id, McpClientMethod.TOOLS_LIST);
        if (cursor != null) {
            McpListToolsParams p = new McpListToolsParams();
            p.setCursor(cursor);
            setParams(p);
        }
    }
}
