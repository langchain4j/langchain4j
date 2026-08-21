package dev.langchain4j.model.anthropic.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonDeserialize(builder = AnthropicOutputConfig.Builder.class)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicOutputConfig {

    @JsonProperty
    private final AnthropicFormat format;

    private AnthropicOutputConfig(Builder builder) {
        this.format = builder.format;
    }

    public AnthropicFormat getFormat() {
        return format;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "AnthropicOutputConfig[" + "format" + format + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(format);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AnthropicOutputConfig outputConfig && equalsTo(outputConfig);
    }

    public boolean equalsTo(AnthropicOutputConfig other) {
        return Objects.equals(format, other.format);
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private AnthropicFormat format;

        public Builder format(AnthropicFormat format) {
            this.format = format;
            return this;
        }

        public AnthropicOutputConfig build() {
            return new AnthropicOutputConfig(this);
        }
    }
}
