package dev.langchain4j.model.googleai;

import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.Map;
import java.util.stream.Collectors;

class SchemaMapper {

    /**
     * Whether {@link #fromJsonSchemaToGSchema} can convert this element and everything below it.
     * Callers that have an alternative, such as sending plain JSON Schema, should ask this first
     * instead of catching the exception the mapper throws.
     *
     * <p>The listed types are exactly the ones the mapper handles. Anything else, including
     * {@link JsonRawSchema}, {@link JsonReferenceSchema} and any element type added later, is
     * reported as not mappable, so a new element type takes the alternative instead of throwing.
     */
    static boolean canBeMapped(JsonSchemaElement jsonSchema) {
        if (jsonSchema instanceof JsonObjectSchema jsonObjectSchema) {
            // Definitions exist to be referenced, and a reference has no typed form.
            return jsonObjectSchema.definitions().isEmpty()
                    && jsonObjectSchema.properties().values().stream().allMatch(SchemaMapper::canBeMapped);
        }
        if (jsonSchema instanceof JsonArraySchema jsonArraySchema) {
            return jsonArraySchema.items() == null || canBeMapped(jsonArraySchema.items());
        }
        if (jsonSchema instanceof JsonAnyOfSchema jsonAnyOfSchema) {
            return jsonAnyOfSchema.anyOf().stream().allMatch(SchemaMapper::canBeMapped);
        }
        return jsonSchema instanceof JsonStringSchema
                || jsonSchema instanceof JsonBooleanSchema
                || jsonSchema instanceof JsonNumberSchema
                || jsonSchema instanceof JsonIntegerSchema
                || jsonSchema instanceof JsonEnumSchema
                || jsonSchema instanceof JsonNullSchema;
    }

    static GeminiSchema fromJsonSchemaToGSchema(JsonSchema jsonSchema) {
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
