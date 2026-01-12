package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Internal
public class McpReadResourceRequest extends McpJsonRpcMessage {

    @JsonInclude
    public final McpClientMethod method = McpClientMethod.RESOURCES_READ;

    @JsonInclude
    private Map<String, Object> params;

    public McpReadResourceRequest(Long id, String uri) {
        super(id);
        this.params = new HashMap<>();
        Objects.requireNonNull(uri);
        this.params.put("uri", uri);
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
