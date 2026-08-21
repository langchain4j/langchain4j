package dev.langchain4j.exception;

/**
 * Thrown when the model provider rejects a request because the caller has exceeded
 * its allowed request or token quota within a time window (HTTP 429).
 * <p>
 * This is a {@link RetriableException}: the request may succeed once the rate-limit
 * window resets. Callers should implement an exponential back-off strategy before
 * retrying.
 */
public class RateLimitException extends RetriableException {
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
