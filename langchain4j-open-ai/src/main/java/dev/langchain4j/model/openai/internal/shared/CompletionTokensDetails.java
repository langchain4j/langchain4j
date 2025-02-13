package dev.langchain4j.model.openai.internal.shared;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = CompletionTokensDetails.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class CompletionTokensDetails {

    @JsonProperty
    private final Integer reasoningTokens;

    public CompletionTokensDetails(Builder builder) {
        this.reasoningTokens = builder.reasoningTokens;
    }

    public Integer reasoningTokens() {
        return reasoningTokens;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof CompletionTokensDetails
                && equalTo((CompletionTokensDetails) another);
    }

    private boolean equalTo(CompletionTokensDetails another) {
        return Objects.equals(reasoningTokens, another.reasoningTokens);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(reasoningTokens);
        return h;
    }

    @Override
    public String toString() {
        return "CompletionTokensDetails{"
                + "reasoningTokens=" + reasoningTokens
                + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private Integer reasoningTokens;

        public Builder reasoningTokens(Integer reasoningTokens) {
            this.reasoningTokens = reasoningTokens;
            return this;
        }

        public CompletionTokensDetails build() {
            return new CompletionTokensDetails(this);
        }
    }
}
