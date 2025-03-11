package dev.langchain4j.exception;

public abstract class RecoverableChatException extends LangChain4jException {
    public RecoverableChatException(String message) {
        super(message);
    }

    public RecoverableChatException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public RecoverableChatException(String message, Throwable cause) {
        super(message, cause);
    }
}
