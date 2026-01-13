package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;
import java.util.List;

@Internal
public class McpCallToolResult extends McpJsonRpcMessage {

    private final Result result;

    public McpCallToolResult(Long id, Result result) {
        super(id);
        this.result = result;
    }

    public Result getResult() {
        return result;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Result {

        private final List<Content> content;
        private final Object structuredContent;
        private final Boolean isError;

        public Result(List<Content> content, Object structuredContent, Boolean isError) {
            this.content = content;
            this.structuredContent = structuredContent;
            this.isError = isError;
        }

        public List<Content> getContent() {
            return content;
        }

        public Object getStructuredContent() {
            return structuredContent;
        }

        public Boolean getIsError() {
            return isError;
        }
    }

    public static class Content {

        private final String type;
        private final String text;

        public Content(String type, String text) {
            this.type = type;
            this.text = text;
        }

        public String getType() {
            return type;
        }

        public String getText() {
            return text;
        }
    }
}
