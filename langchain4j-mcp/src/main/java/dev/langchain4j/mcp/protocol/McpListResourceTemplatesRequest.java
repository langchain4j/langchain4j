package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code ListResourceTemplatesRequest} type from the MCP schema.
 */
@Internal
public class McpListResourceTemplatesRequest extends McpClientRequest {

    public McpListResourceTemplatesRequest(Long id) {
        super(id, McpClientMethod.RESOURCES_TEMPLATES_LIST);
    }
}
