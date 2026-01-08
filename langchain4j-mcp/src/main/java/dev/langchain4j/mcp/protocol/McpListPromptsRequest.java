package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;

@Internal
public class McpListPromptsRequest extends McpJsonRpcMessage {

    @JsonInclude
    public final McpClientMethod method = McpClientMethod.PROMPTS_LIST;

    public McpListPromptsRequest(Long id) {
        super(id);
    }
}
