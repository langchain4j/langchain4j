package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.util.Map;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonDeserialize(builder = MistralAiJsonSchema.Builder.class)
public class MistralAiJsonSchema {

    private final String name;
    private final String description;
    private final Map<String, Object> schema;
    private final boolean strict;

    public MistralAiJsonSchema(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.schema = builder.schema;
        this.strict = builder.strict;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getSchema() {
        return schema;
    }

    public boolean isStrict() {
        return strict;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String name;
        private String description;
        private Map<String, Object> schema;
        private boolean strict;

        private Builder() {}

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder schema(Map<String, Object> schema) {
            this.schema = schema;
            return this;
        }

        public Builder strict(boolean strict) {
            this.strict = strict;
            return this;
        }

        public MistralAiJsonSchema build() {
            return new MistralAiJsonSchema(this);
        }
    }

    public static MistralAiJsonSchema fromJsonSchema(JsonSchema schema, boolean strict) {
        return MistralAiJsonSchema.builder()
                .name(schema.name())
                .schema(toMap(schema.rootElement(), strict))
                .strict(strict)
                .build();
    }

    public static MistralAiJsonSchema fromJsonSchema(JsonSchema schema) {
        return fromJsonSchema(schema, false);
    }
}
