package dev.langchain4j.exception;

public class LangChain4jException extends RuntimeException {

    public LangChain4jException(String message) {
        super(message);
    }

    public LangChain4jException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public LangChain4jException(String message, Throwable cause) {
        super(message, cause);
    }
}
