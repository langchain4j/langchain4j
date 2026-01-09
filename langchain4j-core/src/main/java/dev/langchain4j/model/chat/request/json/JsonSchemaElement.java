package dev.langchain4j.model.chat.request.json;

/**
 * A base interface for a JSON schema element.
 *
 */
public sealed interface JsonSchemaElement
        permits JsonAnyOfSchema,
                JsonArraySchema,
                JsonBooleanSchema,
                JsonEnumSchema,
                JsonIntegerSchema,
                JsonNullSchema,
                JsonNumberSchema,
                JsonObjectSchema,
                JsonRawSchema,
                JsonReferenceSchema,
                JsonStringSchema {

    String description();
}
