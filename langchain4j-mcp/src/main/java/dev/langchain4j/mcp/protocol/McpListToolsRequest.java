package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;
import java.util.HashMap;
import java.util.Map;

@Internal
public class McpListToolsRequest extends McpJsonRpcMessage {

    @JsonInclude
    public final McpClientMethod method = McpClientMethod.TOOLS_LIST;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> params;

    public McpListToolsRequest(final Long id) {
        super(id);
        this.params = new HashMap<>();
    }

    public Map<String, Object> getParams() {
        return params;
    }

    @JsonIgnore
    public void setCursor(String cursor) {
        this.params.put("cursor", cursor);
    }
}
