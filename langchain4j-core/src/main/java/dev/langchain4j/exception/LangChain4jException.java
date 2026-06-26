package dev.langchain4j.exception;

/**
 * The root unchecked exception for all LangChain4j-specific errors.
 * <p>
 * All library exceptions extend this class, making it the single catch point
 * for callers that need to handle any LangChain4j error uniformly.
 * For more targeted handling, prefer catching a specific subclass such as
 * {@link RetriableException} or {@link NonRetriableException}.
 */
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
