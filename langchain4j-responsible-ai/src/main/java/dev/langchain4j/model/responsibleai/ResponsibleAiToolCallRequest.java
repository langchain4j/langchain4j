package dev.langchain4j.model.responsibleai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
class ResponsibleAiToolCallRequest {

    @JsonProperty("tool_name")
    private final String toolName;

    @JsonProperty("tool_params")
    private final Map<String, Object> toolParams;

    @JsonProperty("agent_context")
    private final Map<String, Object> agentContext;

    @JsonProperty("allowed_tools")
    private final List<String> allowedTools;

    private ResponsibleAiToolCallRequest(Builder builder) {
        this.toolName = builder.toolName;
        this.toolParams = builder.toolInput;
        this.agentContext = builder.agentContext != null ? Map.of("description", builder.agentContext) : null;
        this.allowedTools = builder.allowedTools;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String toolName;
        private Map<String, Object> toolInput;
        private String agentContext;
        private List<String> allowedTools;

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder toolInput(Map<String, Object> toolInput) {
            this.toolInput = toolInput;
            return this;
        }

        public Builder agentContext(String agentContext) {
            this.agentContext = agentContext;
            return this;
        }

        public Builder allowedTools(List<String> allowedTools) {
            this.allowedTools = allowedTools;
            return this;
        }

        public ResponsibleAiToolCallRequest build() {
            return new ResponsibleAiToolCallRequest(this);
        }
    }
}
