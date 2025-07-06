package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GeminiThinkingConfig {

    @JsonProperty
    private Boolean includeThoughts;
    @JsonProperty
    private Integer thinkingBudget;

    private GeminiThinkingConfig(Builder builder) {
        this.includeThoughts = builder.includeThoughts;
        this.thinkingBudget = builder.thinkingBudget;
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
