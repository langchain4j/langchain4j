package dev.langchain4j.model.googleai;

import java.util.Objects;

public class GeminiThinkingConfig {
    private Boolean includeThoughts;
    private Integer thinkingBudget;

    private GeminiThinkingConfig(Builder builder) {
        this.includeThoughts = builder.includeThoughts;
        this.thinkingBudget = builder.thinkingBudget;
    }

    public Boolean getIncludeThoughts() {
        return includeThoughts;
    }

    public Integer getThinkingBudget() {
        return thinkingBudget;
    }

    public void setEnableThinking(Boolean includeThoughts) {
        this.includeThoughts = includeThoughts;
    }

    public void setThinkingBudget(final Integer thinkingBudget) {
        this.thinkingBudget = thinkingBudget;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeminiThinkingConfig)) return false;
        GeminiThinkingConfig other = (GeminiThinkingConfig) o;
        return Objects.equals(includeThoughts, other.includeThoughts)
                && Objects.equals(thinkingBudget, other.thinkingBudget);
    }

    @Override
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + (includeThoughts == null ? 43 : includeThoughts.hashCode());
        result = result * PRIME + (thinkingBudget == null ? 43 : thinkingBudget.hashCode());
        return result;
    }

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
            return new GeminiThinkingConfig(this);
        }
    }
}
