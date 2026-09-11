package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;
import java.util.Map;

/**
 * Corresponds to the {@code params} of the {@code CallToolRequest} type from the MCP schema.
 */
@Internal
public class McpCallToolParams extends McpClientParams {

    private String name;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Map<String, Object> arguments;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> inputResponses;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object requestState;

    public McpCallToolParams() {}

    public McpCallToolParams(String name, Map<String, Object> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }

    public Map<String, Object> getInputResponses() {
        return inputResponses;
    }

    public void setInputResponses(Map<String, Object> inputResponses) {
        this.inputResponses = inputResponses;
    }

    public Object getRequestState() {
        return requestState;
    }

    public void setRequestState(Object requestState) {
        this.requestState = requestState;
    }
}
