package dev.langchain4j.exception;

public abstract class UnrecoverableChatException extends LangChain4jException {
    public UnrecoverableChatException(String message) {
        super(message);
    }

    public UnrecoverableChatException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public UnrecoverableChatException(String message, Throwable cause) {
        super(message, cause);
    }
}
