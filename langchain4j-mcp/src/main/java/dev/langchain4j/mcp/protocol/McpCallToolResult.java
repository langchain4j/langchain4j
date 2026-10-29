package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;
import java.util.List;
import java.util.Map;

/**
 * Corresponds to the {@code CallToolResult} type from the MCP schema.
 */
@Internal
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpCallToolResult extends McpJsonRpcMessage {

    private final Result result;

    @JsonCreator
    public McpCallToolResult(@JsonProperty("id") Long id, @JsonProperty("result") Result result) {
        super(id);
        this.result = result;
    }

    public Result getResult() {
        return result;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        private final List<Map<String, Object>> content;
        private final Object structuredContent;
        private final Boolean isError;
        private final Map<String, Object> meta;

        public Result(List<Map<String, Object>> content, Object structuredContent, Boolean isError) {
            this(content, structuredContent, isError, null);
        }

        @JsonCreator
        public Result(
                @JsonProperty("content") List<Map<String, Object>> content,
                @JsonProperty("structuredContent") Object structuredContent,
                @JsonProperty("isError") Boolean isError,
                @JsonProperty("_meta") Map<String, Object> meta) {
            this.content = content;
            this.structuredContent = structuredContent;
            this.isError = isError;
            this.meta = meta;
        }

        public List<Map<String, Object>> getContent() {
            return content;
        }

        public Object getStructuredContent() {
            return structuredContent;
        }

        public Boolean getIsError() {
            return isError;
        }

        @JsonProperty("_meta")
        public Map<String, Object> getMeta() {
            return meta;
        }
    }
}
