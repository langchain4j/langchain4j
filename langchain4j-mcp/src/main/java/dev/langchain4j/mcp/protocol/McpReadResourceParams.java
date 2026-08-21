package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.Internal;
import java.util.Map;
import java.util.Objects;

/**
 * Corresponds to the {@code params} of the {@code ReadResourceRequest} type from the MCP schema.
 */
@Internal
public class McpReadResourceParams extends McpClientParams {

    private String uri;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> inputResponses;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private JsonNode requestState;

    public McpReadResourceParams() {}

    public McpReadResourceParams(String uri) {
        Objects.requireNonNull(uri);
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public Map<String, Object> getInputResponses() {
        return inputResponses;
    }

    public void setInputResponses(Map<String, Object> inputResponses) {
        this.inputResponses = inputResponses;
    }

    public JsonNode getRequestState() {
        return requestState;
    }

    public void setRequestState(JsonNode requestState) {
        this.requestState = requestState;
    }
}
