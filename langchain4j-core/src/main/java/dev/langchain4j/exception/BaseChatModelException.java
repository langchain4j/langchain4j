package dev.langchain4j.exception;

public abstract class BaseChatModelException extends RuntimeException {
    public BaseChatModelException(String message) {
        super(message);
    }

    public BaseChatModelException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public BaseChatModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
