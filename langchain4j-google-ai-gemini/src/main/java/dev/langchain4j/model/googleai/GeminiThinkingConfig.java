package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GeminiThinkingConfig(Boolean includeThoughts, Integer thinkingBudget) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Boolean includeThoughts;
        private Integer thinkingBudget;

        public Builder includeThoughts(Boolean includeThoughts) {
            this.includeThoughts = includeThoughts;
            return this;
        }

        public Builder thinkingBudget(Integer thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
            return this;
        }

        public GeminiThinkingConfig build() {
            return new GeminiThinkingConfig(includeThoughts, thinkingBudget);
        }
    }
}
