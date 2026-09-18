package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.Objects;

@JsonDeserialize(builder = PromptCacheBreakpoint.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class PromptCacheBreakpoint {

    @JsonProperty
    private final String mode;

    public PromptCacheBreakpoint(Builder builder) {
        this.mode = builder.mode;
    }

    public String mode() {
        return mode;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof PromptCacheBreakpoint && equalTo((PromptCacheBreakpoint) another);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(PromptCacheBreakpoint another) {
        return Objects.equals(mode, another.mode);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(mode);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "PromptCacheBreakpoint{" + "mode=" + mode + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String mode;

        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        public PromptCacheBreakpoint build() {
            return new PromptCacheBreakpoint(this);
        }
    }
}
