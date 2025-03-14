package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
@JsonDeserialize(builder = MistralAiResponseFormat.MistralAiResponseFormatBuilder.class)
public class MistralAiResponseFormat {
    private Object type;
    private MistralAiJsonSchema jsonSchema;

    private MistralAiResponseFormat(MistralAiResponseFormatBuilder builder) {
        this.type = builder.type;
        this.jsonSchema = builder.jsonSchema;
    }

    public Object getType() {
        return this.type;
    }

    public MistralAiJsonSchema getJsonSchema() {
        return this.jsonSchema;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MistralAiResponseFormat that = (MistralAiResponseFormat) o;
        return Objects.equals(type, that.type) && Objects.equals(jsonSchema, that.jsonSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, jsonSchema);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "MistralAiResponseFormat [", "]")
                .add("type=" + this.getType())
                .add("jsonSchema=" + this.jsonSchema)
                .toString();
    }

    public static MistralAiResponseFormat fromType(MistralAiResponseFormatType type) {
        return MistralAiResponseFormat.builder().type(type.toString()).build();
    }

    public static MistralAiResponseFormat fromSchema(JsonSchema schema) {
        MistralAiJsonSchema mistralAiJsonSchema = MistralAiJsonSchema.fromJsonSchema(schema);
        return MistralAiResponseFormat.builder()
                .type(MistralAiResponseFormatType.JSON_SCHEMA)
                .jsonSchema(mistralAiJsonSchema)
                .build();
    }

    public static MistralAiResponseFormatBuilder builder() {
        return new MistralAiResponseFormatBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class MistralAiResponseFormatBuilder {
        private Object type;
        private MistralAiJsonSchema jsonSchema;

        private MistralAiResponseFormatBuilder() {}

        /**
         * @return {@code this}.
         */
        public MistralAiResponseFormatBuilder type(Object type) {
            this.type = type;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiResponseFormatBuilder jsonSchema(MistralAiJsonSchema jsonSchema) {
            this.jsonSchema = jsonSchema;
            return this;
        }

        public MistralAiResponseFormat build() {
            return new MistralAiResponseFormat(this);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class MistralAiJsonSchema {
        private String name;
        private String description;
        private Map<String, Object> schema;
        private boolean strict;

        @Override
        public String toString() {
            return "MistralAiJsonSchema{" + "name='"
                    + name + '\'' + ", description='"
                    + description + '\'' + ", strict="
                    + strict + ", schema="
                    + schema + '}';
        }

        public static MistralAiJsonSchema fromJsonSchema(JsonSchema schema, boolean strict) {
            MistralAiJsonSchema newSchema = new MistralAiJsonSchema();
            newSchema.setSchema(JsonSchemaElementHelper.toMap(schema.rootElement()));
            newSchema.setStrict(strict);
            newSchema.setName(schema.name());
            return newSchema;
        }

        public static MistralAiJsonSchema fromJsonSchema(JsonSchema schema) {
            return fromJsonSchema(schema, false);
        }

        public boolean isStrict() {
            return strict;
        }

        public Map<String, Object> getSchema() {
            return schema;
        }

        public String getDescription() {
            return description;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public void setDescription(final String description) {
            this.description = description;
        }

        public void setSchema(final Map<String, Object> schema) {
            this.schema = schema;
        }

        public void setStrict(final boolean strict) {
            this.strict = strict;
        }
    }
}
