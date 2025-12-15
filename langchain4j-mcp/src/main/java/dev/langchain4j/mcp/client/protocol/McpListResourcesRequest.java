package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;

@Internal
public class McpListResourcesRequest extends McpClientMessage {

    @JsonInclude
    public final McpClientMethod method = McpClientMethod.RESOURCES_LIST;

    public McpListResourcesRequest(final Long id) {
        super(id);
    }
}
