package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code ListResourcesRequest} type from the MCP schema.
 */
@Internal
public class McpListResourcesRequest extends McpClientRequest {

    public McpListResourcesRequest(Long id, String cursor) {
        super(id, McpClientMethod.RESOURCES_LIST);
        if (cursor != null) {
            McpListResourcesParams p = new McpListResourcesParams();
            p.setCursor(cursor);
            setParams(p);
        }
    }
}
