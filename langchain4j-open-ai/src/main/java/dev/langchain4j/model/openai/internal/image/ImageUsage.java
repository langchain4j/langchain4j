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

@JsonDeserialize(builder = ImageUsage.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ImageUsage {

    @JsonProperty
    private final Integer inputTokens;

    @JsonProperty
    private final Integer outputTokens;

    @JsonProperty
    private final Integer totalTokens;

    @JsonProperty
    private final TokensDetails inputTokensDetails;

    @JsonProperty
    private final TokensDetails outputTokensDetails;

    public ImageUsage(Builder builder) {
        this.inputTokens = builder.inputTokens;
        this.outputTokens = builder.outputTokens;
        this.totalTokens = builder.totalTokens;
        this.inputTokensDetails = builder.inputTokensDetails;
        this.outputTokensDetails = builder.outputTokensDetails;
    }

    public Integer inputTokens() {
        return inputTokens;
    }

    public Integer outputTokens() {
        return outputTokens;
    }

    public Integer totalTokens() {
        return totalTokens;
    }

    public TokensDetails inputTokensDetails() {
        return inputTokensDetails;
    }

    public TokensDetails outputTokensDetails() {
        return outputTokensDetails;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        if (another == null || getClass() != another.getClass()) return false;
        ImageUsage that = (ImageUsage) another;
        return Objects.equals(inputTokens, that.inputTokens)
                && Objects.equals(outputTokens, that.outputTokens)
                && Objects.equals(totalTokens, that.totalTokens)
                && Objects.equals(inputTokensDetails, that.inputTokensDetails)
                && Objects.equals(outputTokensDetails, that.outputTokensDetails);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        return Objects.hash(inputTokens, outputTokens, totalTokens, inputTokensDetails, outputTokensDetails);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "ImageUsage{"
                + "inputTokens=" + inputTokens
                + ", outputTokens=" + outputTokens
                + ", totalTokens=" + totalTokens
                + ", inputTokensDetails=" + inputTokensDetails
                + ", outputTokensDetails=" + outputTokensDetails
                + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private Integer inputTokens;
        private Integer outputTokens;
        private Integer totalTokens;
        private TokensDetails inputTokensDetails;
        private TokensDetails outputTokensDetails;

        public Builder inputTokens(Integer inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        public Builder outputTokens(Integer outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        public Builder totalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        public Builder inputTokensDetails(TokensDetails inputTokensDetails) {
            this.inputTokensDetails = inputTokensDetails;
            return this;
        }

        public Builder outputTokensDetails(TokensDetails outputTokensDetails) {
            this.outputTokensDetails = outputTokensDetails;
            return this;
        }

        public ImageUsage build() {
            return new ImageUsage(this);
        }
    }

    @JsonDeserialize(builder = TokensDetails.TokensDetailsBuilder.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class TokensDetails {

        @JsonProperty
        private final Integer imageTokens;

        @JsonProperty
        private final Integer textTokens;

        public TokensDetails(TokensDetailsBuilder builder) {
            this.imageTokens = builder.imageTokens;
            this.textTokens = builder.textTokens;
        }

        public Integer imageTokens() {
            return imageTokens;
        }

        public Integer textTokens() {
            return textTokens;
        }

        @Override
        @JacocoIgnoreCoverageGenerated
        public boolean equals(Object another) {
            if (this == another) return true;
            if (another == null || getClass() != another.getClass()) return false;
            TokensDetails that = (TokensDetails) another;
            return Objects.equals(imageTokens, that.imageTokens) && Objects.equals(textTokens, that.textTokens);
        }

        @Override
        @JacocoIgnoreCoverageGenerated
        public int hashCode() {
            return Objects.hash(imageTokens, textTokens);
        }

        @Override
        @JacocoIgnoreCoverageGenerated
        public String toString() {
            return "TokensDetails{" + "imageTokens=" + imageTokens + ", textTokens=" + textTokens + '}';
        }

        public static TokensDetailsBuilder builder() {
            return new TokensDetailsBuilder();
        }

        @JsonPOJOBuilder(withPrefix = "")
        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        public static class TokensDetailsBuilder {

            private Integer imageTokens;
            private Integer textTokens;

            public TokensDetailsBuilder imageTokens(Integer imageTokens) {
                this.imageTokens = imageTokens;
                return this;
            }

            public TokensDetailsBuilder textTokens(Integer textTokens) {
                this.textTokens = textTokens;
                return this;
            }

            public TokensDetails build() {
                return new TokensDetails(this);
            }
        }
    }
}
