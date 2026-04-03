package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;
import java.util.List;
import java.util.Map;

@Internal
public class McpListToolsResult extends McpJsonRpcMessage {

    private final Result result;

    public McpListToolsResult(Long id, Result result) {
        super(id);
        this.result = result;
    }

    public Result getResult() {
        return result;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Result {

        private final List<Map<String, Object>> tools;
        private final String nextCursor;

        public Result(List<Map<String, Object>> tools, String nextCursor) {
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
