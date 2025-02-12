package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;

public class McpCallToolRequest extends McpClientMessage {

    @JsonInclude
    public final ClientMethod method = ClientMethod.TOOLS_CALL;

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
