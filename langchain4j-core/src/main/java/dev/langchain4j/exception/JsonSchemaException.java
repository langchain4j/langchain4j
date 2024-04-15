package dev.langchain4j.exception;

public class JsonSchemaException extends Exception {

    public JsonSchemaException(String message) {
        super(message);
    }

    public JsonSchemaException(Throwable throwable) {
        super(throwable);
    }
}
