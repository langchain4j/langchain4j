package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashMap;
import java.util.Map;

public class McpPingResponse extends McpClientMessage {

    // has to be an empty object
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private final Map<String, Object> result = new HashMap<>();

    public McpPingResponse(final Long id) {
        super(id);
    }

    public Map<String, Object> getResult() {
        return result;
    }
}
