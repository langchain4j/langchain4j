package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

/**
 * Only used with the legacy MCP protocol (versions up to 2025-11-25).
 */
@Internal
public class McpUnsubscribeResourceRequest extends McpClientRequest {

    public McpUnsubscribeResourceRequest(Long id, String uri) {
        super(id, McpClientMethod.RESOURCES_UNSUBSCRIBE);
        setParams(new McpUnsubscribeResourceParams(uri));
    }
}
