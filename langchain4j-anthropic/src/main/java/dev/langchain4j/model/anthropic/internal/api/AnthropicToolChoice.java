package dev.langchain4j.model.anthropic.internal.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static dev.langchain4j.model.anthropic.internal.api.AnthropicToolChoiceType.AUTO;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicToolChoiceType.TOOL;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AnthropicToolChoice {

    @JsonProperty
    private AnthropicToolChoiceType type = AUTO;

    @JsonProperty
    private String name;

    private AnthropicToolChoice(Builder builder) {
        this.type = builder.type;
        this.name = builder.name;
    }

    public static AnthropicToolChoice from(AnthropicToolChoiceType type) {
        return new Builder().type(type).build();
    }

    public static AnthropicToolChoice from(String functionName) {
        return new Builder().name(functionName).type(TOOL).build();
    }

    public static final class Builder {

        private AnthropicToolChoiceType type;
        private String name;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(AnthropicToolChoiceType type) {
            this.type = type;
            return this;
        }

        public AnthropicToolChoice build() {
            return new AnthropicToolChoice(this);
        }
    }
}
