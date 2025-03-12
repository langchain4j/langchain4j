package dev.langchain4j.exception;

public class InvalidRequestException extends NonRetriableException {
    public InvalidRequestException(String message) {
        super(message);
    }

    public InvalidRequestException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
