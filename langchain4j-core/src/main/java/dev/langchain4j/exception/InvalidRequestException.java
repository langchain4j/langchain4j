package dev.langchain4j.exception;

/**
 * Thrown when the model provider rejects a request because the request itself is
 * malformed or violates the provider's constraints (HTTP 4xx, excluding 401, 403,
 * 404, 408, and 429).
 * <p>
 * This is a {@link NonRetriableException}: the same request will be rejected again
 * without modification. The caller must correct the request (e.g. reduce the prompt
 * length, remove unsupported parameters) before retrying.
 *
 * @see ContentFilteredException
 */
public class InvalidRequestException extends NonRetriableException {
    public InvalidRequestException(String message) {
        super(message);
    }

    public InvalidRequestException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
