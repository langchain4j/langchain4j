package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.model.anthropic.AnthropicThinkingEffort;
import java.util.Objects;

@JsonDeserialize(builder = AnthropicOutputConfig.Builder.class)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicOutputConfig {

    @JsonProperty
    private final AnthropicFormat format;

    /**
     * Soft guidance for how much thinking Claude should do when using adaptive thinking
     * ({@code thinking.type = "adaptive"}).
     * <ul>
     *   <li>{@code "max"}    – always think with no constraints (Opus 4.6 only).</li>
     *   <li>{@code "high"}   – always think; deep reasoning (default).</li>
     *   <li>{@code "medium"} – moderate thinking; may skip thinking for simple queries.</li>
     *   <li>{@code "low"}    – minimal thinking; skips thinking for simple tasks.</li>
     * </ul>
     */
    @JsonProperty
    private final AnthropicThinkingEffort effort;

    private AnthropicOutputConfig(Builder builder) {
        this.format = builder.format;
        this.effort = builder.effort;
    }

    public AnthropicFormat getFormat() {
        return format;
    }

    public AnthropicThinkingEffort getEffort() {
        return effort;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "AnthropicOutputConfig[format=" + format + ", effort=" + effort + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, effort);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AnthropicOutputConfig outputConfig && equalsTo(outputConfig);
    }

    public boolean equalsTo(AnthropicOutputConfig other) {
        return Objects.equals(format, other.format) && Objects.equals(effort, other.effort);
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private AnthropicFormat format;
        private AnthropicThinkingEffort effort;

        public Builder format(AnthropicFormat format) {
            this.format = format;
            return this;
        }

        /**
         * Sets the effort level for adaptive thinking.
         */
        public Builder effort(AnthropicThinkingEffort effort) {
            this.effort = effort;
            return this;
        }

        public AnthropicOutputConfig build() {
            return new AnthropicOutputConfig(this);
        }
    }
}
