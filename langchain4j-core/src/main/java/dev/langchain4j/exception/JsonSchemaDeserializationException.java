package dev.langchain4j.exception;

public class JsonSchemaDeserializationException extends JsonSchemaException {

    public JsonSchemaDeserializationException(String message) {
        super(message);
    }

    public JsonSchemaDeserializationException(Throwable cause) {
        super(cause);
    }
}
