package dev.langchain4j.model.anthropic.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AnthropicCountTokensRequest {

    private String model;
    private List<AnthropicMessage> messages;
    private List<AnthropicTextContent> system;
    private List<AnthropicTool> tools;
    private AnthropicThinking thinking;

    public AnthropicCountTokensRequest(Builder builder) {
        this.thinking = builder.thinking;
        this.tools = builder.tools;
        this.system = builder.system;
        this.messages = builder.messages;
        this.model = builder.model;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<AnthropicMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<AnthropicMessage> messages) {
        this.messages = messages;
    }

    public List<AnthropicTextContent> getSystem() {
        return system;
    }

    public void setSystem(List<AnthropicTextContent> system) {
        this.system = system;
    }

    public List<AnthropicTool> getTools() {
        return tools;
    }

    public void setTools(List<AnthropicTool> tools) {
        this.tools = tools;
    }

    public AnthropicThinking getThinking() {
        return thinking;
    }

    public void setThinking(AnthropicThinking thinking) {
        this.thinking = thinking;
    }

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
            return new AnthropicCountTokensRequest(this);
        }
    }
}
