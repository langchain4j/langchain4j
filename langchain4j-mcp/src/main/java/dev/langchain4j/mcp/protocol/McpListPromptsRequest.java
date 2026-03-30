package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code ListPromptsRequest} type from the MCP schema.
 */
@Internal
public class McpListPromptsRequest extends McpClientRequest {

    public McpListPromptsRequest(Long id) {
        super(id, McpClientMethod.PROMPTS_LIST);
    }
}
