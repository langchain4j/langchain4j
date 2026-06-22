package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The 'Icon' object from the MCP protocol schema.
 */
public record McpIcon(
        @JsonProperty("mimeType") String mimeType,
        @JsonProperty("sizes") List<String> sizes,
        @JsonProperty("src") String src,
        @JsonProperty("theme") McpIconTheme theme) {

    @JsonCreator
    public McpIcon {
        sizes = sizes == null ? List.of() : List.copyOf(sizes);
    }
}
