package dev.langchain4j.exception;

public class ModelNotFoundException extends UnrecoverableException {
    public ModelNotFoundException(String message) {
        super(message);
    }

    public ModelNotFoundException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public ModelNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
