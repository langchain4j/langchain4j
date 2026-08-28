package dev.langchain4j.exception;

/**
 * Thrown when the model provider's host cannot be resolved (DNS failure or
 * {@link java.nio.channels.UnresolvedAddressException}).
 * <p>
 * This is a {@link NonRetriableException}: the configured endpoint URL is
 * either incorrect or the host is unreachable from the current network. The
 * caller should verify the base URL in the client configuration before retrying.
 */
public class UnresolvedModelServerException extends NonRetriableException {
    public UnresolvedModelServerException(String message) {
        super(message);
    }

    public UnresolvedModelServerException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public UnresolvedModelServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
