package dev.langchain4j.exception;

public class UnresolvedModelServerException extends NonRetriableException {
    public UnresolvedModelServerException(String message) {
        super(message);
    }

    public UnresolvedModelServerException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public UnresolvedModelServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
