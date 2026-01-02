package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;
import dev.langchain4j.mcp.client.McpRoot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Internal
public class McpRootsListResponse extends McpJsonRpcMessage {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private final Map<String, Object> result = new HashMap<>();

    public McpRootsListResponse(Long id, List<McpRoot> roots) {
        super(id);
        result.put("roots", roots);
    }

    public Map<String, Object> getResult() {
        return result;
    }
}
