package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicOutputFormatType.JSON_SCHEMA;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicSchema;

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

@JsonDeserialize(builder = AnthropicOutputFormat.Builder.class)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicOutputFormat {

    @JsonProperty
    private final AnthropicOutputFormatType type;

    @JsonProperty
    private final Map<String, Object> schema;

    private AnthropicOutputFormat(Builder builder) {
        this.type = builder.type;
        this.schema = builder.schema;
    }

    public AnthropicOutputFormatType getType() {
        return type;
    }

    public Map<String, Object> getSchema() {
        return schema;
    }

    public static AnthropicOutputFormat fromJsonSchema(JsonSchema schema) {
        return AnthropicOutputFormat.builder()
                .type(JSON_SCHEMA)
                .schema(toAnthropicSchema(schema.rootElement()))
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "AnthropicOutputFormat[" + "type" + type + ", jsonSchema" + schema + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, schema);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AnthropicOutputFormat responseFormat && equalsTo(responseFormat);
    }

    public boolean equalsTo(AnthropicOutputFormat other) {
        return Objects.equals(type, other.type) && Objects.equals(schema, other.schema);
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {
        private AnthropicOutputFormatType type;
        private Map<String, Object> schema;

        public Builder type(AnthropicOutputFormatType type) {
            this.type = type;
            return this;
        }

        public Builder schema(Map<String, Object> schema) {
            this.schema = schema;
            return this;
        }

        public AnthropicOutputFormat build() {
            return new AnthropicOutputFormat(this);
        }
    }
}
