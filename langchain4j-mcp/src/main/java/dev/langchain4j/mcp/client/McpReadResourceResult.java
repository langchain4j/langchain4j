package dev.langchain4j.mcp.client;

import java.util.List;
import java.util.Objects;

/**
 * The 'ReadResourceResult' object from the MCP protocol schema.
 */
public class McpReadResourceResult {

    private final List<McpResourceContents> contents;

    public McpReadResourceResult(List<McpResourceContents> contents) {
        this.contents = contents;
    }

    public List<McpResourceContents> contents() {
        return contents;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (McpReadResourceResult) obj;
        return Objects.equals(this.contents, that.contents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contents);
    }

    @Override
    public String toString() {
        return "McpReadResourceResult[" +
                "contents=" + contents + ']';
    }
}
