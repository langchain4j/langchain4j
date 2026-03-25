package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;
import java.util.Objects;

/**
 * Corresponds to the {@code params} of the {@code ReadResourceRequest} type from the MCP schema.
 */
@Internal
public class McpReadResourceParams extends McpClientParams {

    private String uri;

    public McpReadResourceParams() {}

    public McpReadResourceParams(String uri) {
        Objects.requireNonNull(uri);
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }
}
