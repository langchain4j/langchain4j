package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashMap;
import java.util.Map;

public class McpGetPromptRequest extends McpClientMessage {

    @JsonInclude
    public final ClientMethod method = ClientMethod.PROMPTS_GET;

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
