package dev.langchain4j.exception;

/**
 * Thrown when a request is rejected because the supplied credentials are missing,
 * invalid, or insufficient (HTTP 401 / 403).
 * <p>
 * This is a {@link NonRetriableException}: retrying the same request with the same
 * credentials will not help. The caller should verify the API key or token and
 * reconfigure the client before retrying.
 */
public class AuthenticationException extends NonRetriableException {
    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
