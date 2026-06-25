package dev.langchain4j.exception;

/**
 * Thrown when a request to the model provider does not complete within the
 * configured time limit (HTTP 408 or a client-side read/connect timeout).
 * <p>
 * This is a {@link RetriableException}: a transient network condition or a
 * temporarily overloaded provider may have caused the delay, and a retry
 * (potentially with a longer timeout) may succeed.
 */
public class TimeoutException extends RetriableException {
    public TimeoutException(String message) {
        super(message);
    }

    public TimeoutException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public TimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
