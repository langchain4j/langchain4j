package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;
import java.util.Map;

/**
 * Corresponds to the {@code params} of the {@code JSONRPCRequest} type from the MCP schema.
 */
@Internal
public class McpClientParams {

    @JsonProperty("_meta")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> meta;

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }
}
