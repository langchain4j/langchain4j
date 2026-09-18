package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;
import dev.langchain4j.mcp.client.McpGetPromptResult;

/**
 * Corresponds to the {@code GetPromptResult} type from the MCP schema.
 *
 * <p>Named for the response rather than the result because {@link McpGetPromptResult} already
 * exists as the client-facing type.
 */
@Internal
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpGetPromptResponse extends McpJsonRpcMessage {

    private final McpGetPromptResult result;

    @JsonCreator
    public McpGetPromptResponse(
            @JsonProperty("id") Long id, @JsonProperty("result") McpGetPromptResult result) {
        super(id);
        this.result = result;
    }

    public McpGetPromptResult getResult() {
        return result;
    }
}
