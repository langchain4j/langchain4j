package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;

/**
 * The pagination envelope shared by every {@code list} operation in the MCP schema. Only the
 * cursor is modelled, so that paging can be read from any list response without caring which
 * kind of list it carries.
 */
@Internal
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpPaginatedResult extends McpJsonRpcMessage {

    private final Result result;

    @JsonCreator
    public McpPaginatedResult(@JsonProperty("id") Long id, @JsonProperty("result") Result result) {
        super(id);
        this.result = result;
    }

    public Result getResult() {
        return result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        private final String nextCursor;

        @JsonCreator
        public Result(@JsonProperty("nextCursor") String nextCursor) {
            this.nextCursor = nextCursor;
        }

        public String getNextCursor() {
            return nextCursor;
        }
    }
}
