package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.Internal;
import java.util.HashMap;
import java.util.Map;

@Internal
public class McpCallToolRequest extends McpClientMessage {

    @JsonInclude
    private Map<String, Object> params;

    public McpCallToolRequest(Long id, String toolName, ObjectNode arguments) {
        super(id, McpClientMethod.TOOLS_CALL);
        this.params = new HashMap<>();
        this.params.put("name", toolName);
        this.params.put("arguments", arguments);
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
