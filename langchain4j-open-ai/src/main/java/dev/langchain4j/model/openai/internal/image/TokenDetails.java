package dev.langchain4j.model.openai.internal.image;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.Objects;

/**
 * Per-direction token breakdown returned by gpt-image-* models. Used for both
 * {@code input_tokens_details} and {@code output_tokens_details} in the response, since both
 * carry the same {@code text_tokens} / {@code image_tokens} shape.
 */
@JsonDeserialize(builder = TokenDetails.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TokenDetails {

    @JsonProperty
    private final Integer textTokens;

    @JsonProperty
    private final Integer imageTokens;

    public TokenDetails(Builder builder) {
        this.textTokens = builder.textTokens;
        this.imageTokens = builder.imageTokens;
    }

    public Integer textTokens() {
        return textTokens;
    }

    public Integer imageTokens() {
        return imageTokens;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenDetails that = (TokenDetails) o;
        return Objects.equals(textTokens, that.textTokens) && Objects.equals(imageTokens, that.imageTokens);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        return Objects.hash(textTokens, imageTokens);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "TokenDetails{textTokens=" + textTokens + ", imageTokens=" + imageTokens + '}';
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

        public TokenDetails build() {
            return new TokenDetails(this);
        }
    }
}
