package dev.langchain4j.model.responsibleai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
class ResponsibleAiToolResultRequest {

    @JsonProperty("tool_name")
    private final String toolName;

    @JsonProperty("tool_result")
    private final Map<String, Object> toolResult;

    @JsonProperty("agent_context")
    private final Map<String, Object> agentContext;

    @JsonProperty("redact_pii")
    private final Boolean redactPii;

    private ResponsibleAiToolResultRequest(Builder builder) {
        this.toolName = builder.toolName;
        this.toolResult = builder.toolResult != null ? Map.of("raw", builder.toolResult) : null;
        this.agentContext = builder.agentContext != null ? Map.of("description", builder.agentContext) : null;
        this.redactPii = builder.redactPii;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String toolName;
        private String toolResult;
        private String agentContext;
        private Boolean redactPii;

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder toolResult(String toolResult) {
            this.toolResult = toolResult;
            return this;
        }

        public Builder agentContext(String agentContext) {
            this.agentContext = agentContext;
            return this;
        }

        public Builder redactPii(Boolean redactPii) {
            this.redactPii = redactPii;
            return this;
        }

        public ResponsibleAiToolResultRequest build() {
            return new ResponsibleAiToolResultRequest(this);
        }
    }
}
