package dev.langchain4j.model.chat.request.json;

/**
 * A base interface for a JSON schema element.
 *
 * @see JsonAnyOfSchema
 * @see JsonArraySchema
 * @see JsonBooleanSchema
 * @see JsonEnumSchema
 * @see JsonIntegerSchema
 * @see JsonNullSchema
 * @see JsonNumberSchema
 * @see JsonObjectSchema
 * @see JsonReferenceSchema
 * @see JsonStringSchema
 */
public interface JsonSchemaElement {

    String description();
}
