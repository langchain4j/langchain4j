package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

public class McpListResourceTemplatesRequest extends McpClientMessage {

    @JsonInclude
    public final ClientMethod method = ClientMethod.RESOURCES_TEMPLATES_LIST;

    public McpListResourceTemplatesRequest(final Long id) {
        super(id);
    }
}
