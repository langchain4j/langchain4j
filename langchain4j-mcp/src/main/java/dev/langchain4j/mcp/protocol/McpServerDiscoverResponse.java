package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;
import java.util.List;
import java.util.Map;

/**
 * Corresponds to the result of {@code server/discover} in the modern MCP protocol.
 *
 * <p>Also covers the fields a multi-round-trip response carries, since those arrive in the same
 * {@code result} object.
 */
@Internal
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpServerDiscoverResponse extends McpJsonRpcMessage {

    private final Result result;

    @JsonCreator
    public McpServerDiscoverResponse(@JsonProperty("id") Long id, @JsonProperty("result") Result result) {
        super(id);
        this.result = result;
    }

    public Result getResult() {
        return result;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        private final List<String> supportedVersions;
        private final Map<String, Object> capabilities;
        private final Map<String, Object> meta;
        private final String instructions;
        private final String resultType;
        private final Object requestState;
        private final Object inputRequests;

        @JsonCreator
        public Result(
                @JsonProperty("supportedVersions") List<String> supportedVersions,
                @JsonProperty("capabilities") Map<String, Object> capabilities,
                @JsonProperty("_meta") Map<String, Object> meta,
                @JsonProperty("instructions") String instructions,
                @JsonProperty("resultType") String resultType,
                @JsonProperty("requestState") Object requestState,
                @JsonProperty("inputRequests") Object inputRequests) {
            this.supportedVersions = supportedVersions;
            this.capabilities = capabilities;
            this.meta = meta;
            this.instructions = instructions;
            this.resultType = resultType;
            this.requestState = requestState;
            this.inputRequests = inputRequests;
        }

        public List<String> getSupportedVersions() {
            return supportedVersions;
        }

        public Map<String, Object> getCapabilities() {
            return capabilities;
        }

        @JsonProperty("_meta")
        public Map<String, Object> getMeta() {
            return meta;
        }

        public String getInstructions() {
            return instructions;
        }

        public String getResultType() {
            return resultType;
        }

        public Object getRequestState() {
            return requestState;
        }

        public Object getInputRequests() {
            return inputRequests;
        }
    }
}
