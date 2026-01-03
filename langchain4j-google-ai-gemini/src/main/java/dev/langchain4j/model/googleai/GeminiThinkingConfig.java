package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GeminiThinkingConfig(Boolean includeThoughts, Integer thinkingBudget, String thinkingLevel) {

    public enum GeminiThinkingLevel {
        MINIMAL,
        LOW,
        MEDIUM,
        HIGH
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Boolean includeThoughts;
        private Integer thinkingBudget;
        private String thinkingLevel;

        public Builder includeThoughts(Boolean includeThoughts) {
            this.includeThoughts = includeThoughts;
            return this;
        }

        public Builder thinkingBudget(Integer thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
            return this;
        }

        public Builder thinkingLevel(String thinkingLevel) {
            this.thinkingLevel = thinkingLevel;
            return this;
        }

        public Builder thinkingLevel(GeminiThinkingLevel thinkingLevel) {
            this.thinkingLevel = thinkingLevel.toString().toLowerCase();
            return this;
        }

        public GeminiThinkingConfig build() {
            return new GeminiThinkingConfig(includeThoughts, thinkingBudget, thinkingLevel);
        }
    }
}
