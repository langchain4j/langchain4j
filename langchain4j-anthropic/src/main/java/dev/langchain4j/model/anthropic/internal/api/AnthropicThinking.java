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

    @JsonProperty
    private final String display;

    public AnthropicThinking(Builder builder) {
        this.type = builder.type;
        this.budgetTokens = builder.budgetTokens;
        this.display = builder.display;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String type;
        private Integer budgetTokens;
        private String display;

        private Builder() {}

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder budgetTokens(Integer budgetTokens) {
            this.budgetTokens = budgetTokens;
            return this;
        }

        /**
         * Controls how thinking content is returned in API responses.
         *
         * @param display {@code "summarized"} to receive summarized thinking text,
         *                {@code "omitted"} to suppress thinking text (default on Opus 4.7+).
         */
        public Builder display(String display) {
            this.display = display;
            return this;
        }

        public AnthropicThinking build() {
            return new AnthropicThinking(this);
        }
    }
}
