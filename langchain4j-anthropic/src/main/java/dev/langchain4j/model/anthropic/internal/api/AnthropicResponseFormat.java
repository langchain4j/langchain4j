package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicResponseFormatType.JSON_SCHEMA;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicMap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.util.Map;
import java.util.Objects;

@JsonDeserialize(builder = AnthropicResponseFormat.Builder.class)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicResponseFormat {

    @JsonProperty
    private final AnthropicResponseFormatType type;

    @JsonProperty
    private final Map<String, Object> schema;

    private AnthropicResponseFormat(Builder builder) {
        this.type = builder.type;
        this.schema = builder.schema;
    }

    public AnthropicResponseFormatType getType() {
        return type;
    }

    public Map<String, Object> getSchema() {
        return schema;
    }

    public static AnthropicResponseFormat fromJsonSchema(JsonSchema schema) {
        return AnthropicResponseFormat.builder()
                .type(JSON_SCHEMA)
                .schema(toAnthropicMap(schema.rootElement()))
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "AnthropicResponseFormat[" + "type" + type + ", jsonSchema" + schema + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, schema);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AnthropicResponseFormat responseFormat && equalsTo(responseFormat);
    }

    public boolean equalsTo(AnthropicResponseFormat other) {
        return Objects.equals(type, other.type) && Objects.equals(schema, other.schema);
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {
        private AnthropicResponseFormatType type;
        private Map<String, Object> schema;

        public Builder type(AnthropicResponseFormatType type) {
            this.type = type;
            return this;
        }

        public Builder schema(Map<String, Object> schema) {
            this.schema = schema;
            return this;
        }

        public AnthropicResponseFormat build() {
            return new AnthropicResponseFormat(this);
        }
    }
}
