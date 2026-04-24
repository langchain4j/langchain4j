package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code PingRequest} type from the MCP schema.
 */
@Internal
public class McpPingRequest extends McpClientRequest {

    public McpPingRequest(Long id) {
        super(id, McpClientMethod.PING);
    }
}
