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
 * Token-usage block returned by gpt-image-* models. Absent on dall-e responses.
 *
 * <p>Find description of fields
 * <a href="https://platform.openai.com/docs/api-reference/images/object">here</a>.
 */
@JsonDeserialize(builder = Usage.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Usage {

    @JsonProperty
    private final Integer totalTokens;

    @JsonProperty
    private final Integer inputTokens;

    @JsonProperty
    private final Integer outputTokens;

    @JsonProperty
    private final TokenDetails inputTokensDetails;

    @JsonProperty
    private final TokenDetails outputTokensDetails;

    public Usage(Builder builder) {
        this.totalTokens = builder.totalTokens;
        this.inputTokens = builder.inputTokens;
        this.outputTokens = builder.outputTokens;
        this.inputTokensDetails = builder.inputTokensDetails;
        this.outputTokensDetails = builder.outputTokensDetails;
    }

    public Integer totalTokens() {
        return totalTokens;
    }

    public Integer inputTokens() {
        return inputTokens;
    }

    public Integer outputTokens() {
        return outputTokens;
    }

    public TokenDetails inputTokensDetails() {
        return inputTokensDetails;
    }

    public TokenDetails outputTokensDetails() {
        return outputTokensDetails;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Usage that = (Usage) o;
        return Objects.equals(totalTokens, that.totalTokens)
                && Objects.equals(inputTokens, that.inputTokens)
                && Objects.equals(outputTokens, that.outputTokens)
                && Objects.equals(inputTokensDetails, that.inputTokensDetails)
                && Objects.equals(outputTokensDetails, that.outputTokensDetails);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        return Objects.hash(totalTokens, inputTokens, outputTokens, inputTokensDetails, outputTokensDetails);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "Usage{totalTokens=" + totalTokens
                + ", inputTokens=" + inputTokens
                + ", outputTokens=" + outputTokens
                + ", inputTokensDetails=" + inputTokensDetails
                + ", outputTokensDetails=" + outputTokensDetails
                + '}';
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private Integer totalTokens;
        private Integer inputTokens;
        private Integer outputTokens;
        private TokenDetails inputTokensDetails;
        private TokenDetails outputTokensDetails;

        public Builder totalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        public Builder inputTokens(Integer inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        public Builder outputTokens(Integer outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        public Builder inputTokensDetails(TokenDetails inputTokensDetails) {
            this.inputTokensDetails = inputTokensDetails;
            return this;
        }

        public Builder outputTokensDetails(TokenDetails outputTokensDetails) {
            this.outputTokensDetails = outputTokensDetails;
            return this;
        }

        public Usage build() {
            return new Usage(this);
        }
    }
}
