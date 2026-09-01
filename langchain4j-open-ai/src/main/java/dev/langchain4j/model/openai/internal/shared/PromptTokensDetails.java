package dev.langchain4j.model.openai.internal.shared;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.Objects;

@JsonDeserialize(builder = PromptTokensDetails.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class PromptTokensDetails {

    @JsonProperty
    private final Integer cachedTokens;

    @JsonProperty
    private final Integer cacheWriteTokens;

    public PromptTokensDetails(Builder builder) {
        this.cachedTokens = builder.cachedTokens;
        this.cacheWriteTokens = builder.cacheWriteTokens;
    }

    public Integer cachedTokens() {
        return cachedTokens;
    }

    public Integer cacheWriteTokens() {
        return cacheWriteTokens;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof PromptTokensDetails && equalTo((PromptTokensDetails) another);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(PromptTokensDetails another) {
        return Objects.equals(cachedTokens, another.cachedTokens)
                && Objects.equals(cacheWriteTokens, another.cacheWriteTokens);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(cachedTokens);
        h += (h << 5) + Objects.hashCode(cacheWriteTokens);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "PromptTokensDetails{" + "cachedTokens=" + cachedTokens + ", cacheWriteTokens=" + cacheWriteTokens + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private Integer cachedTokens;
        private Integer cacheWriteTokens;

        public Builder cachedTokens(Integer cachedTokens) {
            this.cachedTokens = cachedTokens;
            return this;
        }

        public Builder cacheWriteTokens(Integer cacheWriteTokens) {
            this.cacheWriteTokens = cacheWriteTokens;
            return this;
        }

        public PromptTokensDetails build() {
            return new PromptTokensDetails(this);
        }
    }
}
