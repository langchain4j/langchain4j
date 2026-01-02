package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.Internal;
import java.util.HashMap;
import java.util.Map;

@Internal
public class McpCallToolRequest extends McpJsonRpcMessage {

    @JsonInclude
    public final McpClientMethod method = McpClientMethod.TOOLS_CALL;

    @JsonInclude
    private Map<String, Object> params;

    public McpCallToolRequest(final Long id, String toolName, ObjectNode arguments) {
        super(id);
        this.params = new HashMap<>();
        this.params.put("name", toolName);
        this.params.put("arguments", arguments);
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
