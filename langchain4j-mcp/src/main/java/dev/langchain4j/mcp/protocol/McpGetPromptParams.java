package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;
import java.util.Map;

/**
 * Corresponds to the {@code params} of the {@code GetPromptRequest} type from the MCP schema.
 */
@Internal
public class McpGetPromptParams extends McpClientParams {

    private String name;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Map<String, Object> arguments;

    public McpGetPromptParams() {}

    public McpGetPromptParams(String name, Map<String, Object> arguments) {
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
}
