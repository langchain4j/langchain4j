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
public class AnthropicCreateMessageRequest {

    public String model;
    public List<AnthropicMessage> messages;
    public List<AnthropicTextContent> system;
    public int maxTokens;
    public List<String> stopSequences;
    public boolean stream;
    public Double temperature;
    public Double topP;
    public Integer topK;
    public List<AnthropicTool> tools;
    public AnthropicToolChoice toolChoice;
    public AnthropicThinking thinking;

    public AnthropicCreateMessageRequest() {}

    public AnthropicCreateMessageRequest(
            String model,
            List<AnthropicMessage> messages,
            List<AnthropicTextContent> system,
            int maxTokens,
            List<String> stopSequences,
            boolean stream,
            Double temperature,
            Double topP,
            Integer topK,
            List<AnthropicTool> tools,
            AnthropicToolChoice toolChoice,
            AnthropicThinking thinking) {
        this.model = model;
        this.messages = messages;
        this.system = system;
        this.maxTokens = maxTokens;
        this.stopSequences = stopSequences;
        this.stream = stream;
        this.temperature = temperature;
        this.topP = topP;
        this.topK = topK;
        this.tools = tools;
        this.toolChoice = toolChoice;
        this.thinking = thinking;
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

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public List<String> getStopSequences() {
        return stopSequences;
    }

    public void setStopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
    }

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public List<AnthropicTool> getTools() {
        return tools;
    }

    public void setTools(List<AnthropicTool> tools) {
        this.tools = tools;
    }

    public AnthropicToolChoice getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(AnthropicToolChoice toolChoice) {
        this.toolChoice = toolChoice;
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

    public Builder toBuilder() {
        return new Builder().model(this.model)
                .messages(this.messages)
                .system(this.system)
                .maxTokens(this.maxTokens)
                .stopSequences(this.stopSequences)
                .stream(this.stream)
                .temperature(this.temperature)
                .topP(this.topP)
                .topK(this.topK)
                .tools(this.tools)
                .toolChoice(this.toolChoice)
                .thinking(this.thinking);
    }

    public static class Builder {

        private String model;
        private List<AnthropicMessage> messages;
        private List<AnthropicTextContent> system;
        private int maxTokens;
        private List<String> stopSequences;
        private boolean stream;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private List<AnthropicTool> tools;
        private AnthropicToolChoice toolChoice;
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

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public Builder stream(boolean stream) {
            this.stream = stream;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder tools(List<AnthropicTool> tools) {
            this.tools = tools;
            return this;
        }

        public Builder toolChoice(AnthropicToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public Builder thinking(AnthropicThinking thinking) {
            this.thinking = thinking;
            return this;
        }

        public AnthropicCreateMessageRequest build() {
            return new AnthropicCreateMessageRequest(
                    model,
                    messages,
                    system,
                    maxTokens,
                    stopSequences,
                    stream,
                    temperature,
                    topP,
                    topK,
                    tools,
                    toolChoice,
                    thinking);
        }
    }
}
