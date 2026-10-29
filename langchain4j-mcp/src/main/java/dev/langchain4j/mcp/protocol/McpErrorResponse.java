package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code JSONRPCError} type from the MCP schema.
 */
@Internal
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpErrorResponse extends McpJsonRpcMessage {

    private final Error error;

    @JsonCreator
    public McpErrorResponse(@JsonProperty("id") Long id, @JsonProperty("error") Error error) {
        super(id);
        this.error = error;
    }

    public Error getError() {
        return error;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Error {

        private final int code;
        private final String message;
        private final Object data;

        @JsonCreator
        public Error(
                @JsonProperty("code") int code,
                @JsonProperty("message") String message,
                @JsonProperty("data") Object data) {
            this.code = code;
            this.message = message;
            this.data = data;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public Object getData() {
            return data;
        }
    }
}
