package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;
import java.util.HashMap;
import java.util.Map;

@Internal
public class McpPingResponse extends McpJsonRpcMessage {

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
