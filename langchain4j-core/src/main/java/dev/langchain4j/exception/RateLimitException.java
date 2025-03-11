package dev.langchain4j.exception;

public class RateLimitException extends RecoverableChatException {
    public RateLimitException(String message) {
        super(message);
    }

    public RateLimitException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
