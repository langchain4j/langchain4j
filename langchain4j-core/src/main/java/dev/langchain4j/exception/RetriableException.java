package dev.langchain4j.exception;

public class RetriableException extends LangChain4jException {
    public RetriableException(String message) {
        super(message);
    }

    public RetriableException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public RetriableException(String message, Throwable cause) {
        super(message, cause);
    }
}
