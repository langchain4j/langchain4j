package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code ReadResourceRequest} type from the MCP schema.
 */
@Internal
public class McpReadResourceRequest extends McpClientRequest {

    public McpReadResourceRequest(Long id, String uri) {
        super(id, McpClientMethod.RESOURCES_READ);
        setParams(new McpReadResourceParams(uri));
    }
}
