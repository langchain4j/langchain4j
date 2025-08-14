package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;

@Internal
public class McpListResourceTemplatesRequest extends McpClientMessage {

    @JsonInclude
    public final McpClientMethod method = McpClientMethod.RESOURCES_TEMPLATES_LIST;

    public McpListResourceTemplatesRequest(final Long id) {
        super(id);
    }
}
