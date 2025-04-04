package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MistralAiJsonSchema {
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
