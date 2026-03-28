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
public class Usage {

    @JsonProperty
    private final Integer totalTokens;
    @JsonProperty
    private final Integer inputTokens;
    @JsonProperty
    private final Integer outputTokens;
    @JsonProperty
    private final InputTokenDetails inputTokenDetails;

    public Usage(Builder builder) {
        this.totalTokens = builder.totalTokens;
        this.inputTokens = builder.inputTokens;
        this.outputTokens = builder.outputTokens;
        this.inputTokenDetails = builder.inputTokenDetails;
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

    public InputTokenDetails inputTokenDetails() {
        return inputTokenDetails;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        if (another == null || getClass() != another.getClass()) return false;
        Usage usage = (Usage) another;
        return (
                Objects.equals(totalTokens, usage.totalTokens) &&
                        Objects.equals(inputTokens, usage.inputTokens) &&
                        Objects.equals(outputTokens, usage.outputTokens) &&
                        Objects.equals(inputTokenDetails, usage.inputTokenDetails)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalTokens, inputTokens, outputTokens, inputTokenDetails);
    }

    @Override
    public String toString() {
        return (
                "Usage{" +
                        "totalTokens=" +
                        totalTokens +
                        ", inputTokens=" +
                        inputTokens +
                        ", outputTokens=" +
                        outputTokens +
                        ", inputTokenDetails=" +
                        inputTokenDetails +
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

        private Integer totalTokens;
        private Integer inputTokens;
        private Integer outputTokens;
        private InputTokenDetails inputTokenDetails;

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

        public Builder inputTokenDetails(InputTokenDetails inputTokenDetails) {
            this.inputTokenDetails = inputTokenDetails;
            return this;
        }

        public Usage build() {
            return new Usage(this);
        }
    }
}
