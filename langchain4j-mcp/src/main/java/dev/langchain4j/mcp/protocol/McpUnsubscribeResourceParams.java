package dev.langchain4j.mcp.protocol;

import java.util.Objects;

public class McpUnsubscribeResourceParams extends McpClientParams {

    private String uri;

    public McpUnsubscribeResourceParams(final String uri) {
        this.uri = Objects.requireNonNull(uri);
    }

    public String getUri() {
        return uri;
    }
}
