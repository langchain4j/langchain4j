package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code params} of the {@code ListResourceTemplatesRequest} type from the MCP schema.
 */
@Internal
public class McpListResourceTemplatesParams extends McpClientParams {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String cursor;

    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }
}
