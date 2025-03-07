package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

public class McpListResourcesRequest extends McpClientMessage {

    @JsonInclude
    public final ClientMethod method = ClientMethod.RESOURCES_LIST;

    public McpListResourcesRequest(final Long id) {
        super(id);
    }
}
