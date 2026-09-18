package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code ServerDiscoverRequest} type from the MCP schema.
 */
@Internal
public class McpServerDiscoverRequest extends McpClientRequest {
    public McpServerDiscoverRequest(Long id) {
        super(id, McpClientMethod.SERVER_DISCOVER);
    }
}
