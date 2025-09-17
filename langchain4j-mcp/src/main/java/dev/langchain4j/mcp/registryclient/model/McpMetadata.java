package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class McpMetadata {
    private Long count;

    @JsonProperty("next_cursor")
    private String nextCursor;

    public Long getCount() {
        return count;
    }

    public String getNextCursor() {
        return nextCursor;
    }
}
