package dev.langchain4j.jsonschema;

import dev.langchain4j.exception.JsonSchemaDeserializationException;

public class JsonSchemaSanitizationException extends JsonSchemaDeserializationException {

    public JsonSchemaSanitizationException(String message) {
        super(message);
    }
}
