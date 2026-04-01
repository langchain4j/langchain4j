package dev.langchain4j.mcp.protocol;

import java.util.Objects;

public class McpSubscribeResourceParams extends McpClientParams {

    private String uri;

    public McpSubscribeResourceParams(final String uri) {
        this.uri = Objects.requireNonNull(uri);
    }

    public String getUri() {
        return uri;
    }
}
