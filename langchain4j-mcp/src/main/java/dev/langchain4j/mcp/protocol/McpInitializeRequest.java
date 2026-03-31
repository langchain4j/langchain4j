package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code InitializeRequest} type from the MCP schema.
 */
@Internal
public class McpInitializeRequest extends McpClientRequest {

    public McpInitializeRequest(Long id) {
        super(id, McpClientMethod.INITIALIZE);
    }
}
