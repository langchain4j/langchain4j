package dev.langchain4j.model.googleai.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.Map;
import java.util.stream.Collectors;

@Internal
public class SchemaMapper {
    private SchemaMapper() {}

    public static GeminiSchema fromJsonSchemaToGSchema(JsonSchema jsonSchema) {
        return fromJsonSchemaToGSchema(jsonSchema.rootElement());
    }

    static GeminiSchema fromJsonSchemaToGSchema(JsonSchemaElement jsonSchema) {
        GeminiSchema.GeminiSchemaBuilder schemaBuilder = GeminiSchema.builder();

        if (jsonSchema instanceof JsonStringSchema) {
            JsonStringSchema jsonStringSchema = (JsonStringSchema) jsonSchema;
            schemaBuilder.description(jsonStringSchema.description());
            schemaBuilder.type(GeminiType.STRING);
        } else if (jsonSchema instanceof JsonBooleanSchema) {
            JsonBooleanSchema jsonBooleanSchema = (JsonBooleanSchema) jsonSchema;
            schemaBuilder.description(jsonBooleanSchema.description());
            schemaBuilder.type(GeminiType.BOOLEAN);
        } else if (jsonSchema instanceof JsonNumberSchema) {
            JsonNumberSchema jsonNumberSchema = (JsonNumberSchema) jsonSchema;
            schemaBuilder.description(jsonNumberSchema.description());
            schemaBuilder.type(GeminiType.NUMBER);
        } else if (jsonSchema instanceof JsonIntegerSchema) {
            JsonIntegerSchema jsonIntegerSchema = (JsonIntegerSchema) jsonSchema;
            schemaBuilder.description(jsonIntegerSchema.description());
            schemaBuilder.type(GeminiType.INTEGER);
        } else if (jsonSchema instanceof JsonEnumSchema) {
            JsonEnumSchema jsonEnumSchema = (JsonEnumSchema) jsonSchema;
            schemaBuilder.description(jsonEnumSchema.description());
            schemaBuilder.type(GeminiType.STRING);
            schemaBuilder.enumeration(jsonEnumSchema.enumValues());
        } else if (jsonSchema instanceof JsonObjectSchema) {
            JsonObjectSchema jsonObjectSchema = (JsonObjectSchema) jsonSchema;
            schemaBuilder.description(jsonObjectSchema.description());
            schemaBuilder.type(GeminiType.OBJECT);

            if (jsonObjectSchema.properties() != null) {
                Map<String, JsonSchemaElement> properties = jsonObjectSchema.properties();
                Map<String, GeminiSchema> mappedProperties = properties.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey, entry -> fromJsonSchemaToGSchema(entry.getValue())));
                schemaBuilder.properties(mappedProperties);
            }

            if (jsonObjectSchema.required() != null) {
                schemaBuilder.required(jsonObjectSchema.required());
            }
        } else if (jsonSchema instanceof JsonArraySchema) {
            JsonArraySchema jsonArraySchema = (JsonArraySchema) jsonSchema;
            schemaBuilder.description(jsonArraySchema.description());
            schemaBuilder.type(GeminiType.ARRAY);

            if (jsonArraySchema.items() != null) {
                schemaBuilder.items(fromJsonSchemaToGSchema(jsonArraySchema.items()));
            }
        } else if (jsonSchema instanceof JsonAnyOfSchema) {
            JsonAnyOfSchema jsonAnyOfSchema = (JsonAnyOfSchema) jsonSchema;
            schemaBuilder.description(jsonAnyOfSchema.description());
            schemaBuilder.anyOf(jsonAnyOfSchema.anyOf().stream()
                    .map(SchemaMapper::fromJsonSchemaToGSchema)
                    .collect(Collectors.toList()));
        } else if (jsonSchema instanceof JsonNullSchema) {
            schemaBuilder.type(GeminiType.NULL);
        } else {
            throw new IllegalArgumentException("Unsupported JsonSchemaElement type: " + jsonSchema.getClass());
        }

        return schemaBuilder.build();
    }
}
