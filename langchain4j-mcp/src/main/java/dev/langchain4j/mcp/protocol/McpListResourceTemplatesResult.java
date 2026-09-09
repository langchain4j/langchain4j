package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import java.util.List;

/**
 * Corresponds to the {@code ListResourceTemplatesResult} type from the MCP schema.
 */
@Internal
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpListResourceTemplatesResult extends McpJsonRpcMessage {

    private final Result result;

    @JsonCreator
    public McpListResourceTemplatesResult(@JsonProperty("id") Long id, @JsonProperty("result") Result result) {
        super(id);
        this.result = result;
    }

    public Result getResult() {
        return result;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        private final List<McpResourceTemplate> resourceTemplates;
        private final String nextCursor;

        @JsonCreator
        public Result(
                @JsonProperty("resourceTemplates") List<McpResourceTemplate> resourceTemplates,
                @JsonProperty("nextCursor") String nextCursor) {
            this.resourceTemplates = resourceTemplates;
            this.nextCursor = nextCursor;
        }

        public List<McpResourceTemplate> getResourceTemplates() {
            return resourceTemplates;
        }

        public String getNextCursor() {
            return nextCursor;
        }
    }
}
