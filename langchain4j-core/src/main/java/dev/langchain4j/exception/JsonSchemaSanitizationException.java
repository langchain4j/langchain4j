package dev.langchain4j.exception;

public abstract class JsonSchemaSanitizationException extends JsonSchemaDeserializationException {

    public JsonSchemaSanitizationException(String message) {
        super(message);
    }
}
