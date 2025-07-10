package dev.langchain4j.model.openai.internal.image;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InputTokenDetails {

    @JsonProperty
    private final Integer textTokens;
    @JsonProperty
    private final Integer imageTokens;

    public InputTokenDetails(Builder builder) {
        this.textTokens = builder.textTokens;
        this.imageTokens = builder.imageTokens;
    }

    public Integer textTokens() {
        return textTokens;
    }

    public Integer imageTokens() {
        return imageTokens;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        if (another == null || getClass() != another.getClass()) return false;
        InputTokenDetails that = (InputTokenDetails) another;
        return (
                Objects.equals(textTokens, that.textTokens) &&
                        Objects.equals(imageTokens, that.imageTokens)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(textTokens, imageTokens);
    }

    @Override
    public String toString() {
        return (
                "InputTokenDetails{" +
                        "textTokens=" +
                        textTokens +
                        ", imageTokens=" +
                        imageTokens +
                        '}'
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private Integer textTokens;
        private Integer imageTokens;

        public Builder textTokens(Integer textTokens) {
            this.textTokens = textTokens;
            return this;
        }

        public Builder imageTokens(Integer imageTokens) {
            this.imageTokens = imageTokens;
            return this;
        }

        public InputTokenDetails build() {
            return new InputTokenDetails(this);
        }
    }
}
