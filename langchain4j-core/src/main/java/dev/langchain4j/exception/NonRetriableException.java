package dev.langchain4j.exception;

/**
 * Indicates a permanent error for which retrying the same request will not help.
 * <p>
 * Typical causes include invalid credentials, a malformed request, or referencing
 * a model that does not exist. Callers should inspect the specific subclass to
 * decide how to recover (e.g. prompt the user to correct their input, or fail fast).
 *
 * @see RetriableException
 * @see AuthenticationException
 * @see InvalidRequestException
 * @see ModelNotFoundException
 * @see UnresolvedModelServerException
 */
public class NonRetriableException extends LangChain4jException {
    public NonRetriableException(String message) {
        super(message);
    }

    public NonRetriableException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public NonRetriableException(String message, Throwable cause) {
        super(message, cause);
    }
}
