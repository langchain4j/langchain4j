package dev.langchain4j.exception;

public class InternalServerException extends RetriableException {
    public InternalServerException(String message) {
        super(message);
    }

    public InternalServerException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public InternalServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
