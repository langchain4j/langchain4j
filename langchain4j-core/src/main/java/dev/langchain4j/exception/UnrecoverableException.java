package dev.langchain4j.exception;

public class UnrecoverableException extends RuntimeException {
    public UnrecoverableException(String message) {
        super(message);
    }

    public UnrecoverableException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public UnrecoverableException(String message, Throwable cause) {
        super(message, cause);
    }
}
