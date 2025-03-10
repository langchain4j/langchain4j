package dev.langchain4j.exception;

public abstract class UnrecoverableException extends BaseChatModelException {
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
