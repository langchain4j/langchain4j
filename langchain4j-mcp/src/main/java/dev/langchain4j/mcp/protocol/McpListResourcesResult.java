package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;
import dev.langchain4j.mcp.client.McpResource;
import java.util.List;

/**
 * Corresponds to the {@code ListResourcesResult} type from the MCP schema.
 */
@Internal
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpListResourcesResult extends McpJsonRpcMessage {

    private final Result result;

    @JsonCreator
    public McpListResourcesResult(@JsonProperty("id") Long id, @JsonProperty("result") Result result) {
        super(id);
        this.result = result;
    }

    public Result getResult() {
        return result;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        private final List<McpResource> resources;
        private final String nextCursor;

        @JsonCreator
        public Result(
                @JsonProperty("resources") List<McpResource> resources,
                @JsonProperty("nextCursor") String nextCursor) {
            this.resources = resources;
            this.nextCursor = nextCursor;
        }

        public List<McpResource> getResources() {
            return resources;
        }

        public String getNextCursor() {
            return nextCursor;
        }
    }
}
