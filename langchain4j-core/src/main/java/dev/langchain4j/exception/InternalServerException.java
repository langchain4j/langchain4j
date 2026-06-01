package dev.langchain4j.exception;

/**
 * Thrown when the model provider returns a server-side error (HTTP 5xx).
 * <p>
 * This is a {@link RetriableException}: the failure is on the provider's side
 * and the request may succeed after a brief back-off. If the error persists
 * across multiple retries, it likely indicates an ongoing outage with the provider.
 */
public class InternalServerException extends RetriableException {
    public InternalServerException(String message) {
        super(message);
    }

    public InternalServerException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public InternalServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
