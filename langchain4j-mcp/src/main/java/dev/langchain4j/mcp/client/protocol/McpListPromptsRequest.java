package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

public class McpListPromptsRequest extends McpClientMessage {

    @JsonInclude
    public final ClientMethod method = ClientMethod.PROMPTS_LIST;

    public McpListPromptsRequest(Long id) {
        super(id);
    }
}
