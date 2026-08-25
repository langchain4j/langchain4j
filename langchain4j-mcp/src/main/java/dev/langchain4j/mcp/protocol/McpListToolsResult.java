package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;
import java.util.List;
import java.util.Map;

/**
 * Corresponds to the {@code ListToolsResult} type from the MCP schema.
 */
@Internal
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpListToolsResult extends McpJsonRpcMessage {

    private final Result result;

    @JsonCreator
    public McpListToolsResult(@JsonProperty("id") Long id, @JsonProperty("result") Result result) {
        super(id);
        this.result = result;
    }

    public Result getResult() {
        return result;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        private final List<Map<String, Object>> tools;
        private final String nextCursor;

        @JsonCreator
        public Result(
                @JsonProperty("tools") List<Map<String, Object>> tools,
                @JsonProperty("nextCursor") String nextCursor) {
            this.tools = tools;
            this.nextCursor = nextCursor;
        }

        public List<Map<String, Object>> getTools() {
            return tools;
        }

        public String getNextCursor() {
            return nextCursor;
        }
    }
}
