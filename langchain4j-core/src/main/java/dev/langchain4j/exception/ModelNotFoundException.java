package dev.langchain4j.exception;

/**
 * Thrown when the requested model does not exist or is not accessible by the caller
 * (HTTP 404).
 * <p>
 * This is a {@link NonRetriableException}: the same request will fail again until
 * the model name or identifier is corrected in the client configuration.
 */
public class ModelNotFoundException extends NonRetriableException {
    public ModelNotFoundException(String message) {
        super(message);
    }

    public ModelNotFoundException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public ModelNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
