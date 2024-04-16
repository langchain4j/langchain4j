package dev.langchain4j.exception;

public class JsonSchemaSerializationException extends JsonSchemaException {

    public JsonSchemaSerializationException(String message) {
        super(message);
    }

    public JsonSchemaSerializationException(Throwable cause) {
        super(cause);
    }
}
