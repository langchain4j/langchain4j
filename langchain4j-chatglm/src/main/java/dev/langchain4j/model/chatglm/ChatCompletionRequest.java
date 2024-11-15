package dev.langchain4j.model.chatglm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class ChatCompletionRequest {

    private String prompt;
    private Double temperature;
    private Double topP;
    private Integer maxLength;
    private List<List<String>> history;

    ChatCompletionRequest() {
    }

    ChatCompletionRequest(String prompt, Double temperature, Double topP, Integer maxLength, List<List<String>> history) {
        this.prompt = prompt;
        this.temperature = temperature;
        this.topP = topP;
        this.maxLength = maxLength;
        this.history = history;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
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

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public List<List<String>> getHistory() {
        return history;
    }

    public void setHistory(List<List<String>> history) {
        this.history = history;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private String prompt;
        private Double temperature;
        private Double topP;
        private Integer maxLength;
        private List<List<String>> history;

        Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        Builder maxLength(Integer maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        Builder history(List<List<String>> history) {
            this.history = history;
            return this;
        }

        ChatCompletionRequest build() {
            return new ChatCompletionRequest(prompt, temperature, topP, maxLength, history);
        }
    }
}
