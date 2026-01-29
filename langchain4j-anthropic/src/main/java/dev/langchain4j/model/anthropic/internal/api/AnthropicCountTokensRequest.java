package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public record AnthropicCountTokensRequest(
        String model,
        List<AnthropicMessage> messages,
        List<AnthropicTextContent> system,
        List<AnthropicTool> tools,
        AnthropicThinking thinking) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String model;
        private List<AnthropicMessage> messages;
        private List<AnthropicTextContent> system;
        private List<AnthropicTool> tools;
        private AnthropicThinking thinking;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder messages(List<AnthropicMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder system(List<AnthropicTextContent> system) {
            this.system = system;
            return this;
        }

        public Builder tools(List<AnthropicTool> tools) {
            this.tools = tools;
            return this;
        }

        public Builder thinking(AnthropicThinking thinking) {
            this.thinking = thinking;
            return this;
        }

        public AnthropicCountTokensRequest build() {
            return new AnthropicCountTokensRequest(model, messages, system, tools, thinking);
        }
    }
}
