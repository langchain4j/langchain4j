package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code JSONRPCResponse} type from the MCP schema.
 */
@Internal
public class McpClientResponse extends McpClientMessage {

    public McpClientResponse(Long id) {
        super(id, null);
    }
}
