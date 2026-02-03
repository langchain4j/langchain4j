package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

@Internal
public class McpListResourcesRequest extends McpClientMessage {

    public McpListResourcesRequest(Long id) {
        super(id, McpClientMethod.RESOURCES_LIST);
    }
}
