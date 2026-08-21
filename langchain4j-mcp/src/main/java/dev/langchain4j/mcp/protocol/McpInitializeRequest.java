package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code InitializeRequest} type from the MCP schema.
 * Only used with the legacy MCP protocol (versions up to 2025-11-25).
 */
@Internal
public class McpInitializeRequest extends McpClientRequest {

    public McpInitializeRequest(Long id) {
        super(id, McpClientMethod.INITIALIZE);
    }
}
