package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code ListPromptsRequest} type from the MCP schema.
 */
@Internal
public class McpListPromptsRequest extends McpClientRequest {

    public McpListPromptsRequest(Long id, String cursor) {
        super(id, McpClientMethod.PROMPTS_LIST);
        if (cursor != null) {
            McpListPromptsParams p = new McpListPromptsParams();
            p.setCursor(cursor);
            setParams(p);
        }
    }
}
