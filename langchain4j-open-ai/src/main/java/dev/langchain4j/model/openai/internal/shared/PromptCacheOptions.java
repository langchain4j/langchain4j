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

@JsonDeserialize(builder = PromptCacheOptions.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class PromptCacheOptions {

    @JsonProperty
    private final String mode;

    @JsonProperty
    private final String ttl;

    public PromptCacheOptions(Builder builder) {
        this.mode = builder.mode;
        this.ttl = builder.ttl;
    }

    public String mode() {
        return mode;
    }

    public String ttl() {
        return ttl;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof PromptCacheOptions && equalTo((PromptCacheOptions) another);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(PromptCacheOptions another) {
        return Objects.equals(mode, another.mode) && Objects.equals(ttl, another.ttl);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(mode);
        h += (h << 5) + Objects.hashCode(ttl);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "PromptCacheOptions{" + "mode=" + mode + ", ttl=" + ttl + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String mode;
        private String ttl;

        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        public Builder ttl(String ttl) {
            this.ttl = ttl;
            return this;
        }

        public PromptCacheOptions build() {
            return new PromptCacheOptions(this);
        }
    }
}
