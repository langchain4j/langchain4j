package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;
import dev.langchain4j.mcp.client.McpPrompt;
import java.util.List;

/**
 * Corresponds to the {@code ListPromptsResult} type from the MCP schema.
 */
@Internal
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpListPromptsResult extends McpJsonRpcMessage {

    private final Result result;

    @JsonCreator
    public McpListPromptsResult(@JsonProperty("id") Long id, @JsonProperty("result") Result result) {
        super(id);
        this.result = result;
    }

    public Result getResult() {
        return result;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        private final List<McpPrompt> prompts;
        private final String nextCursor;

        @JsonCreator
        public Result(
                @JsonProperty("prompts") List<McpPrompt> prompts,
                @JsonProperty("nextCursor") String nextCursor) {
            this.prompts = prompts;
            this.nextCursor = nextCursor;
        }

        public List<McpPrompt> getPrompts() {
            return prompts;
        }

        public String getNextCursor() {
            return nextCursor;
        }
    }
}
