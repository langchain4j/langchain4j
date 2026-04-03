package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;

@Internal
public class McpErrorResponse extends McpJsonRpcMessage {

    private final Error error;

    public McpErrorResponse(Long id, Error error) {
        super(id);
        this.error = error;
    }

    public Error getError() {
        return error;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Error {

        private final int code;
        private final String message;
        private final Object data;

        public Error(int code, String message, Object data) {
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
