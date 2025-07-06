package dev.langchain4j.model.anthropic.internal.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AnthropicThinking {

    @JsonProperty
    private final String type;

    @JsonProperty
    private final Integer budgetTokens;

    public AnthropicThinking(Builder builder) {
        this.type = builder.type;
        this.budgetTokens = builder.budgetTokens;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String type;
        private Integer budgetTokens;

        private Builder() {
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder budgetTokens(Integer budgetTokens) {
            this.budgetTokens = budgetTokens;
            return this;
        }

        public AnthropicThinking build() {
            return new AnthropicThinking(this);
        }
    }
}
