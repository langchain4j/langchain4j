package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

/**
 * Only used with the legacy MCP protocol (versions up to 2025-11-25).
 */
@Internal
public class McpSubscribeResourceRequest extends McpClientRequest {

    public McpSubscribeResourceRequest(Long id, String uri) {
        super(id, McpClientMethod.RESOURCES_SUBSCRIBE);
        setParams(new McpSubscribeResourceParams(uri));
    }
}
