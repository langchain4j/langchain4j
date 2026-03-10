package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

@Internal
public class McpListResourceTemplatesRequest extends McpClientMessage {

    public McpListResourceTemplatesRequest(Long id) {
        super(id, McpClientMethod.RESOURCES_TEMPLATES_LIST);
    }
}
