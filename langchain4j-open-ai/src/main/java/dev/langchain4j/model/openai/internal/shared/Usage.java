package dev.langchain4j.model.openai.internal.shared;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = Usage.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class Usage {

    @JsonProperty
    private final Integer totalTokens;
    @JsonProperty
    private final Integer promptTokens;
    @JsonProperty
    private final PromptTokensDetails promptTokensDetails;
    @JsonProperty
    private final Integer completionTokens;
    @JsonProperty
    private final CompletionTokensDetails completionTokensDetails;

    public Usage(Builder builder) {
        this.totalTokens = builder.totalTokens;
        this.promptTokens = builder.promptTokens;
        this.promptTokensDetails = builder.promptTokensDetails;
        this.completionTokens = builder.completionTokens;
        this.completionTokensDetails = builder.completionTokensDetails;
    }

    public Integer totalTokens() {
        return totalTokens;
    }

    public Integer promptTokens() {
        return promptTokens;
    }

    public PromptTokensDetails promptTokensDetails() {
        return promptTokensDetails;
    }

    public Integer completionTokens() {
        return completionTokens;
    }

    public CompletionTokensDetails completionTokensDetails() {
        return completionTokensDetails;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof Usage
                && equalTo((Usage) another);
    }

    private boolean equalTo(Usage another) {
        return Objects.equals(totalTokens, another.totalTokens)
                && Objects.equals(promptTokens, another.promptTokens)
                && Objects.equals(promptTokensDetails, another.promptTokensDetails)
                && Objects.equals(completionTokens, another.completionTokens)
                && Objects.equals(completionTokensDetails, another.completionTokensDetails);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(totalTokens);
        h += (h << 5) + Objects.hashCode(promptTokens);
        h += (h << 5) + Objects.hashCode(promptTokensDetails);
        h += (h << 5) + Objects.hashCode(completionTokens);
        h += (h << 5) + Objects.hashCode(completionTokensDetails);
        return h;
    }

    @Override
    public String toString() {
        return "Usage{"
                + "totalTokens=" + totalTokens
                + ", promptTokens=" + promptTokens
                + ", promptTokensDetails=" + promptTokensDetails
                + ", completionTokens=" + completionTokens
                + ", completionTokensDetails=" + completionTokensDetails
                + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private Integer totalTokens;
        private Integer promptTokens;
        private PromptTokensDetails promptTokensDetails;
        private Integer completionTokens;
        private CompletionTokensDetails completionTokensDetails;

        public Builder totalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        public Builder promptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
            return this;
        }

        public Builder promptTokensDetails(PromptTokensDetails promptTokensDetails) {
            this.promptTokensDetails = promptTokensDetails;
            return this;
        }

        public Builder completionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
            return this;
        }

        public Builder completionTokensDetails(CompletionTokensDetails completionTokensDetails) {
            this.completionTokensDetails = completionTokensDetails;
            return this;
        }

        public Usage build() {
            return new Usage(this);
        }
    }
}
