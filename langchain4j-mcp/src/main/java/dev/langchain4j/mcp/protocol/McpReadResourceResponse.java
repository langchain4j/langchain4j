package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;
import java.util.List;

/**
 * Corresponds to the {@code ReadResourceResult} type from the MCP schema.
 *
 * <p>A content entry carries either {@code text} or {@code blob}; which one decides the
 * {@code McpResourceContents} subtype the client exposes.
 */
@Internal
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpReadResourceResponse extends McpJsonRpcMessage {

    private final Result result;

    @JsonCreator
    public McpReadResourceResponse(@JsonProperty("id") Long id, @JsonProperty("result") Result result) {
        super(id);
        this.result = result;
    }

    public Result getResult() {
        return result;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        private final List<Contents> contents;

        @JsonCreator
        public Result(@JsonProperty("contents") List<Contents> contents) {
            this.contents = contents;
        }

        public List<Contents> getContents() {
            return contents;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Contents {

        private final String uri;
        private final String mimeType;
        private final String text;
        private final String blob;

        @JsonCreator
        public Contents(
                @JsonProperty("uri") String uri,
                @JsonProperty("mimeType") String mimeType,
                @JsonProperty("text") String text,
                @JsonProperty("blob") String blob) {
            this.uri = uri;
            this.mimeType = mimeType;
            this.text = text;
            this.blob = blob;
        }

        public String getUri() {
            return uri;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getText() {
            return text;
        }

        public String getBlob() {
            return blob;
        }
    }
}
