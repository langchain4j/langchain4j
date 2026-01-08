package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;
import java.util.HashMap;
import java.util.Map;

@Internal
public class McpGetPromptRequest extends McpJsonRpcMessage {

    @JsonInclude
    public final McpClientMethod method = McpClientMethod.PROMPTS_GET;

    @JsonInclude
    private Map<String, Object> params;

    public McpGetPromptRequest(Long id, String promptName, Map<String, Object> arguments) {
        super(id);
        this.params = new HashMap<>();
        this.params.put("name", promptName);
        this.params.put("arguments", arguments);
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
