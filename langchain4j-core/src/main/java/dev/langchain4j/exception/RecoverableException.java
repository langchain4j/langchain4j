package dev.langchain4j.exception;

public abstract class RecoverableException extends BaseChatModelException {
    public RecoverableException(String message) {
        super(message);
    }

    public RecoverableException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public RecoverableException(String message, Throwable cause) {
        super(message, cause);
    }
}
