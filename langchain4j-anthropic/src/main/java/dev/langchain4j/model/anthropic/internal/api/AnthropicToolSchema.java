package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicToolSchema {

    public String type = "object";

    @JsonProperty("additionalProperties")
    public Boolean additionalProperties;

    public Map<String, Map<String, Object>> properties;
    public List<String> required;

    public AnthropicToolSchema() {}

    /**
     * @deprecated please use {@link #AnthropicToolSchema(Builder)} instead
     */
    @Deprecated
    public AnthropicToolSchema(String type, Map<String, Map<String, Object>> properties, List<String> required) {
        this.type = type;
        this.properties = properties;
        this.required = required;
    }

    public AnthropicToolSchema(Builder builder) {
        this.type = builder.type;
        this.additionalProperties = builder.additionalProperties;
        this.properties = builder.properties;
        this.required = builder.required;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AnthropicToolSchema that = (AnthropicToolSchema) o;
        return Objects.equals(type, that.type)
                && Objects.equals(additionalProperties, that.additionalProperties)
                && Objects.equals(properties, that.properties)
                && Objects.equals(required, that.required);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, additionalProperties, properties, required);
    }

    @Override
    public String toString() {
        return "AnthropicToolSchema{"
                + "type='" + type + '\''
                + ", additionalProperties=" + additionalProperties
                + ", properties=" + properties
                + ", required=" + required
                + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String type = "object";
        private Boolean additionalProperties;
        private Map<String, Map<String, Object>> properties;
        private List<String> required;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder additionalProperties(Boolean additionalProperties) {
            this.additionalProperties = additionalProperties;
            return this;
        }

        public Builder properties(Map<String, Map<String, Object>> properties) {
            this.properties = properties;
            return this;
        }

        public Builder required(List<String> required) {
            this.required = required;
            return this;
        }

        public AnthropicToolSchema build() {
            return new AnthropicToolSchema(this);
        }
    }
}
