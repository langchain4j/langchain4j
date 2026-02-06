package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

@Internal
public class McpListPromptsRequest extends McpClientMessage {

    public McpListPromptsRequest(Long id) {
        super(id, McpClientMethod.PROMPTS_LIST);
    }
}
