package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public class McpMetadata {
    private Long count;

    @JsonAlias("next_cursor")
    private String nextCursor;

    public Long getCount() {
        return count;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    @Override
    public String toString() {
        return "McpMetadata{" +
                "count=" + count +
                ", nextCursor='" + nextCursor + '\'' +
                '}';
    }
}
