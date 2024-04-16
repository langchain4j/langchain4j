package dev.langchain4j.jsonschema;

import dev.langchain4j.exception.JsonSchemaDeserializationException;

public class JsonSchemaParsingException extends JsonSchemaDeserializationException {

    public JsonSchemaParsingException(String message) {
        super(message);
    }

    public JsonSchemaParsingException(Throwable cause) {
        super(cause);
    }
}
